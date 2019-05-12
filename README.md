cassandra-loader-sample
===

*このリポジトリは、https://github.com/yukim/cassandra-bulkload-example を参考にさせていただいています*

このリポジトリは、csvファイルをSSTableに変換し、Cassandraに一括アップロードするサンプルコードです。以下の流れで行います。

1. CCMを用いてクラスタ作成
1. アップロード対象テーブル作成
1. csvファイル作成
1. SSTableファイル作成
1. アップロード

注意：アップロード実施には、以下のツールのインストールが必要です。

* Java8
* python2.7
* CCM(Cassandra Cluster Manager)
* Maven

## 1. CCM(Cassandra Cluster Manager)を用いてクラスタ作成

localhostにCassndraクラスタを起動します。バージョンは 2.0.11 を指定します。

*このリポジトリのSSTable作成処理は、Cassandraバージョン2.0.11を想定しています。 2.0.11 以外のクラスタへのアップロードを実施したい場合、pom.xmlのcassandraのビルドバージョンを変更して、`BulkLoad.java`の内容を適宜変更してください。*

```aidl
$ ccm create -v 2.0.11 -n 3 sample
$ ccm start
$ ccm status
# 3ノードのクラスタが起動しているのを確認
Cluster: 'sample'
-----------------
node1: UP
node3: UP
node2: UP
```

## 2. アップロード対象テーブル作成

作成したクラスタに、IoTのセンサーデータを格納するテーブルを作成します。

```aidl
$ ccm node1 cqlsh

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

## 3. csvファイル作成

python スクリプトでcsvデータを生成します。データ量は、`bin/generate_data.py`を編集することで調整できます。

```aidl
$ git clone https://github.com/shoshii/cassandra-loader-sample
$ cd cassandra-loader-sample
$ python bin/generate_data.py
# csvファイルが出力されます
$ ls -la input/
total 84096
drwxr-xr-x   4 shoshii  713033059       128 May 10 17:21 .
drwxr-xr-x  13 shoshii  713033059       416 May 12 19:27 ..
-rw-r--r--   1 shoshii  713033059         0 May 10 17:19 .gitkeep
-rw-r--r--   1 shoshii  713033059  42301685 May 12 19:04 sensor_test_data.csv
```

## 4. SSTableファイル作成

csvファイルを元に、JavaコードによりSSTableファイルを作成します。

```aidl
# 実行準備
$ mvn clean package
# SSTableファイル作成
$ mvn exec:java
# SSTableファイルが作成されることを確認
$ ls -la output/sample/sensor_data/
total 65360
drwxr-xr-x  8 shoshii  713033059       256 May 12 19:04 .
drwxr-xr-x  3 shoshii  713033059        96 May 12 19:04 ..
-rw-r--r--  1 shoshii  713033059     10867 May 12 19:04 sample-sensor_data-jb-1-CompressionInfo.db
-rw-r--r--  1 shoshii  713033059  32655593 May 12 19:04 sample-sensor_data-jb-1-Data.db
-rw-r--r--  1 shoshii  713033059        16 May 12 19:04 sample-sensor_data-jb-1-Filter.db
-rw-r--r--  1 shoshii  713033059    108940 May 12 19:04 sample-sensor_data-jb-1-Index.db
-rw-r--r--  1 shoshii  713033059      4416 May 12 19:04 sample-sensor_data-jb-1-Statistics.db
-rw-r--r--  1 shoshii  713033059        79 May 12 19:04 sample-sensor_data-jb-1-TOC.txt
```

## 5. アップロード

`ccm bulkload`というコマンドを実行すると、`sstableloader`というツールが起動し、SSTableファイルをCassandraクラスタにアップロードします。
`sstableloader`により、短時間で効率的に大量データをCassandraに取り込むことができます。

```aidl
$ ccm bulkload output/sample/sensor_data
$ ccm node1 cqlsh
SELECT * FROM sample.sensor_data WHERE sensor_id = 'sensor_1' AND date = '2019-05-13';

...

  sensor_1 | 2019-05-13 |    0 |      2 |     29 |  34.505692117
  sensor_1 | 2019-05-13 |    0 |      1 |     59 | 27.3575299476
  sensor_1 | 2019-05-13 |    0 |      1 |     29 | 17.7115568483
  sensor_1 | 2019-05-13 |    0 |      0 |     59 | 18.7912159966
  sensor_1 | 2019-05-13 |    0 |      0 |     29 | 11.1413248574

(2880 rows)
```

## 6. （応用）セカンダリインデックスを用いた全ノード検索

独自に付与するインデックスを用いて、特定日付の、全センサーデータを検索してみます。

*セカンダリインデックスの利用は原則推奨されません。この例はあくまでも参考とし、内部挙動を十分理解して性能試験を念入りに実施してから商用環境に導入してください*

```aidl
$ ccm node1 cqlsh
CREATE INDEX index_date ON sample.sensor_data (date);

SELECT * FROM sample.sensor_data WHERE date = '2019-05-13' LIMIT 1000000;

...

  sensor_6 | 2019-05-13 |    0 |      3 |     29 | 19.9041144244
  sensor_6 | 2019-05-13 |    0 |      2 |     59 | 15.2077215375
  sensor_6 | 2019-05-13 |    0 |      2 |     29 | 36.6124587107
  sensor_6 | 2019-05-13 |    0 |      1 |     59 |  38.295837205
  sensor_6 | 2019-05-13 |    0 |      1 |     29 | 39.1046182354
  sensor_6 | 2019-05-13 |    0 |      0 |     59 | 26.8736872351
  sensor_6 | 2019-05-13 |    0 |      0 |     29 | 28.4600052594

(28800 rows)
```