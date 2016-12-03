import datetime
import random
import numpy as np
import matplotlib.pyplot as plt

# sudo apt-get install python-tk
# pip2 install numpy matplotlib

def create_block(timestamp, num_pow):
    return {'time_stamp' : timestamp, 'num_pow' : num_pow, 'first_work_factor':0}

def create_work(idx, factor, target):
    return {'id': idx, 'base_executions_per_second' : factor, 'target' : target}

def addSecs(tm, secs):
    fulldate = tm + datetime.timedelta(seconds=secs)
    return fulldate

def randomDuration():
    if do_not_randomize_block_times_but_do_always_60_sec:
        return 60
    else:
        return int(random.uniform(25, 120))

current_time = datetime.datetime.now()

# experiment with the number of work packages
works_to_create = 3

generate_blocks = 100
current_height = 0
blockchain = []
work_packages = []
base_target = 0x000000ffffffffffffffffffffffffff
poisson_distribution = np.random.poisson(5, generate_blocks)
stretch_number_pows = True
do_not_randomize_block_times_but_do_always_60_sec = True
new_miner_every_xth_second = 10
how_many_miners_come_or_go = 70242
initial_miners = 50
miners_kick_in_at_block=50

def currently_active_miners(current_height):
    if current_height<miners_kick_in_at_block:
        return 0
    # get the current active number of miners in relation of blockchain height,
    # but the number of miners increases by 1 every 10 blocks
    increases = int(current_height/new_miner_every_xth_second) * how_many_miners_come_or_go
    return initial_miners+increases

def miner_pows_based_on_target(work, height, dur):
    current_target = work["target"]
    factor = (current_target / base_target) * 1.0*dur/60.0
    actual_pow_mined = work["base_executions_per_second"]
    # random jitter
    actual_pow_mined = abs((actual_pow_mined - 1) + random.uniform(1,2)) * currently_active_miners(height)
    actual_pow_mined = actual_pow_mined *factor
    # rate limit to 20 pows per block
    if actual_pow_mined > 20:
        actual_pow_mined = 20
    if actual_pow_mined < 0:
        actual_pow_mined = 0
    return actual_pow_mined
def kimoto(x):
    return  1 + (0.7084 * pow(((x)/(144)), -1.228));
def retarget_work(block, x):
    targetI = x["target"]
    pastMass = 0
    counter = 0
    current_block = block
    current_block_timestamp = blockchain[current_block]["time_stamp"]
    adjustment = 0
    isFull = True
    fullCnt = 0
    isEmpty = True
    max_block_reading = 144
    emptyCnt = 0
    while isFull or isEmpty:
        if isFull and blockchain[current_block]["num_pow"][x["id"]] == 20:
            fullCnt += 1
        else:
            isFull = False
        if isEmpty and blockchain[current_block]["num_pow"][x["id"]] == 0:
            emptyCnt += 1
        else:
            isEmpty = False
        current_block -= 1
        if current_block < 1:
            break
    current_block = block
    while True:
        counter += 1
        pastMass += blockchain[current_block]["num_pow"][x["id"]]
        if current_block_timestamp < blockchain[current_block-1]["time_stamp"]:
            current_block_timestamp = blockchain[current_block-1]["time_stamp"]
        seconds_passed = (current_block_timestamp - blockchain[current_block-1]["time_stamp"]).seconds
        current_block -= 1
        if seconds_passed < 1:
            seconds_passed = 1
        trs_per_second = float(pastMass) / float(seconds_passed)
        target_per_second = 10.0 / 60.0
        if trs_per_second > 0:
            adjustment = target_per_second / trs_per_second
            kim = kimoto(pastMass * 30)
            if adjustment > kim or adjustment < (1.0/kim):
                break
        else:
            adjustment = 1
        if current_block < 1 or counter == max_block_reading:
            break

    if fullCnt > 1:
        adjustment = adjustment / (1 << fullCnt)
    if emptyCnt > 1:
        adjustment = adjustment * (1 << emptyCnt)
    targetI = targetI * adjustment
    if targetI>base_target:
            targetI = base_target
    if x["id"] == 0:
            blockchain[block]["first_work_factor"] = adjustment
    x["target"] = targetI
    print "Retarget using",counter,"blocks","fullcnt",fullCnt,"emptyCnt",emptyCnt


def retarget_works(block):
    for x in work_packages:
        retarget_work(block,x)

# Here we create up to three different work objects
if works_to_create>=1:
    work_packages.append(create_work(0, 20, base_target))
if works_to_create>=2:
    work_packages.append(create_work(1, 60, base_target))
if works_to_create>=3:
    work_packages.append(create_work(2, 35, base_target))

while current_height < generate_blocks:
    dur = randomDuration()
    current_time = addSecs(current_time,dur) # random block generation time
    block_pows = {}
    for x in work_packages:
        num_pow = miner_pows_based_on_target(x, current_height, dur) # mine some POW depending on the current difficulty
        block_pows[x["id"]] = num_pow
    blockchain.append(create_block(current_time, block_pows))
    retarget_works(current_height) # This retargeting method is the "critical part here"
    current_height = current_height + 1


values = []
target_factors = []
ideal = []
for idx in range(len(blockchain)):
    if idx == 0:
        continue
    x = blockchain[idx]
    x_minus_one = blockchain[idx-1]
    time_passed = (x["time_stamp"] - x_minus_one["time_stamp"]).seconds
    strech_normalizer = time_passed / 60.0
    if stretch_number_pows == False:
        ideal.append(works_to_create*10*strech_normalizer)
    else:
        ideal.append(works_to_create*10)
    sum_x = 0
    for y in x["num_pow"]:
        sum_x += x["num_pow"][y]
    if stretch_number_pows == False:
        values.append(sum_x)
    else:
        values.append(sum_x/strech_normalizer)
x = range(generate_blocks)[1:]
y = values

#fig = plt.figure()
ax0 = plt.subplot(211)
if stretch_number_pows:
    ax0.set_ylabel('POW rate per 60s', color='b')
else:
    ax0.set_ylabel('POWs per Block', color='b')
ax0.set_xlabel('Block height')
ax0.plot(x,y,'-o',x,ideal,'r--')
values = []
ideal = []
target_factors = []
for idx in range(len(blockchain)):
    if idx == 0:
        continue
    x = blockchain[idx]
    x_minus_one = blockchain[idx-1]
    time_passed = (x["time_stamp"] - x_minus_one["time_stamp"]).seconds
    strech_normalizer = time_passed / 60.0
    if stretch_number_pows == False:
        ideal.append(10*strech_normalizer)
    else:
        ideal.append(10)
    sum_x = 0
    sum_x += x["num_pow"][0]
    #print "sumx",sum_x
    if stretch_number_pows == False:
        values.append(sum_x)
    else:
        values.append(sum_x/strech_normalizer)
x = range(generate_blocks)[1:]
y = values
plt.title('All Works: Total POWs')

ax1 = plt.subplot(212)
ax1.plot(x,y,'-o',x,ideal,'r--')
ax1.set_xlabel('Block Height')
# Make the y-axis label and tick labels match the line color.
if stretch_number_pows:
    ax1.set_ylabel('POW rate per 60s', color='b')
else:
    ax1.set_ylabel('POWs per Block', color='b')

for tl in ax1.get_yticklabels():
    tl.set_color('b')



ax2 = ax1.twinx()
ax2.set_ylim(0.4, 1.6)
ax2.bar(x,[x["first_work_factor"] for x in blockchain][1:],0.45,color='#deb0b0', alpha=0.2)
ax2.set_ylabel('Retargeting Factor', color='r')
for tl in ax2.get_yticklabels():
    tl.set_color('r')
plt.title('First Work: POWs + Retargeting Factor')

plt.show()
