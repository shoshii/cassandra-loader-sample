import datetime
import time
import os
import random
import threading

def put_sensor_data(fp, id):
    now_ts = time.time()
    for jdx in range(0, 100000):
        now_ts += 30
        now = datetime.datetime.fromtimestamp(now_ts)
        line = "sensor_%s,%s,%s,%s,%s,%s" % (id, now.strftime("%Y-%m-%d"), now.hour, now.minute, now.second, random.uniform(10, 40))
        fp.write(line + "\n")

with open(os.path.dirname(__file__) + '/../input/sensor_test_data.csv', 'w') as fp:
    threads = []
    max_parallel_num = 10
    parallel_num = 0
    for idx in range(0, 10):
        x = threading.Thread(target=put_sensor_data,
                             args=(fp, idx))
        threads.append(x)
        x.start()
        parallel_num += 1
        if parallel_num >= max_parallel_num:
            for thread in threads:
                thread.join()
            parallel_num = 0
            threads = []
    for thread in threads:
        thread.join()
