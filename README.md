


```aidl
CREATE KEYSPACE sample WITH REPLICATION = 
  {'class':'NetworkTopologyStrategy','datacenter1': 1};
USE sample;

CREATE TABLE sensor_data (
            sensor_id text,
            date text,
            hour int,
            minute int,
            second int,
            temperature text,
            PRIMARY KEY ((sensor_id, date), hour, minute, second)
        ) WITH CLUSTERING ORDER BY (hour DESC, minute DESC, second DESC);
```