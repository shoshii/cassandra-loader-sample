cassandra-loader-sample
===

*このリポジトリは、https://github.com/yukim/cassandra-bulkload-example を参考にさせていただいています*

csvファイルをCassandraに一括アップロードするサンプルコードです。以下の流れで行います。

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

```aidl
$ ccm create -v 2.0.11 -n 3 sample
$ ccm start
$ ccm status
# 3ノードのクラスタが起動する
Cluster: 'sample'
-----------------
node1: UP
node3: UP
node2: UP
```

## 2. アップロード対象テーブル作成

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

```aidl
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

`sstableloader`というツールが起動し、SSTableファイルをCassandraクラスタにアップロードします。

```aidl
$ ccm bulkload output/sample/sensor_data
$ ccm node1 cqlsh
SELECT * FROM sample.sensor_data WHERE sensor_id = 'sensor_1' AND date = '2019-05-13';
```