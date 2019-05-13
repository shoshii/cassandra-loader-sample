package bulkload;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.FileReader;

import org.apache.cassandra.exceptions.InvalidRequestException;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.io.sstable.CQLSSTableWriter;

public class BulkLoad {

    /** Default input file */
    public static final String INPUT_FILE = "./input/sensor_test_data.csv";

    /** Default output directory */
    public static final String DEFAULT_OUTPUT_DIR = "./output";

    /** Keyspace name */
    public static final String KEYSPACE = "sample";
    /** Table name */
    public static final String TABLE = "sensor_data";


    /**
    * Schema for bulk loading table.
    * It is important not to forget adding keyspace name before table name,
    * otherwise CQLSSTableWriter throws exception.
    */
    public static final String SCHEMA = String.format("CREATE TABLE %s.%s (" +
            "sensor_id text, " +
            "date text, " +
            "hour int, " +
            "minute int, " +
            "second int, " +
            "temperature text, " +
            "PRIMARY KEY ((sensor_id, date), hour, minute, second) " +
        ") WITH CLUSTERING ORDER BY (hour DESC, minute DESC, second DESC)", KEYSPACE, TABLE);

    /**
    * INSERT statement to bulk load.
    * It is like prepared statement. You fill in place holder for each data.
    */
    public static final String INSERT_STMT = String.format("INSERT INTO %s.%s (" +
          "sensor_id, date, hour, minute, second, temperature" +
          ") VALUES (" +
          "?, ?, ?, ?, ?, ?" +
          ")", KEYSPACE, TABLE);

    private static void recursiveDeleteFile(final File file) throws Exception {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                recursiveDeleteFile(child);
            }
        }
        file.delete();
    }

    public static void main(String[] args) {
        System.out.println("creating sstable files has started.");
        Config.setClientMode(true);

        // Create output directory that has keyspace and table name in the path
        File outputDir = new File(DEFAULT_OUTPUT_DIR + File.separator + KEYSPACE + File.separator + TABLE);
        if (outputDir.exists())
        {
            try {
                recursiveDeleteFile(outputDir);
                outputDir.mkdirs();
            } catch (Exception e) {
                throw new RuntimeException("Cannot clear output directory: " + outputDir);
            }
        }

        // Prepare SSTable writer
        CQLSSTableWriter.Builder builder = CQLSSTableWriter.builder();
        // set output directory
        builder.inDirectory(outputDir)
                // set target schema
                .forTable(SCHEMA)
                // set CQL statement to put data
                .using(INSERT_STMT)
                // set partitioner if needed
                // default is Murmur3Partitioner so set if you use different one.
                .withPartitioner(new Murmur3Partitioner());
        CQLSSTableWriter writer = builder.build();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(INPUT_FILE));
            CsvListReader csvReader = new CsvListReader(reader, CsvPreference.STANDARD_PREFERENCE);

            csvReader.getHeader(true);

            // Write to SSTable while reading data
            List<String> line;
            while ((line = csvReader.read()) != null)
            {
                // We use Java types here based on
                // http://www.datastax.com/drivers/java/2.0/com/datastax/driver/core/DataType.Name.html#asJavaClass%28%29
                writer.addRow(line.get(0),
                        line.get(1),
                        Integer.parseInt(line.get(2)),
                        Integer.parseInt(line.get(3)),
                        Integer.parseInt(line.get(4)),
                        line.get(5));
            }
            writer.close();
        }
        catch (InvalidRequestException e) {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out.println("creating sstable files has ended.");
    }
}
