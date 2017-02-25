import urllib2
import os.path
import json
import time
import sys
from bitcoin.wallet import CBitcoinSecret, P2PKHBitcoinAddress
from bitcoin.core import x, CScript
from bitcoin.core import b2x, lx, COIN, COutPoint, CMutableTxOut, CMutableTxIn, CMutableTransaction, Hash160
def ensure_is_present(url, file_name):
    exists = os.path.isfile(file_name) 
    if not exists:
        time.sleep(2)
        u = urllib2.urlopen(url)
        f = open(file_name, 'wb')
        meta = u.info()
        try:
            file_size = int(meta.getheaders("Content-Length")[0])
        except:
            file_size = 1000
        print "Downloading: %s Bytes: %s" % (file_name, file_size)

        file_size_dl = 0
        block_sz = 8192
        while True:
            buffer = u.read(block_sz)
            if not buffer:
                break

            file_size_dl += len(buffer)
            f.write(buffer)
            status = r"%10d  [%3.2f%%]" % (file_size_dl, file_size_dl * 100. / file_size)
            status = status + chr(8)*(len(status)+1)
            print status,

        f.close()
        print ""

addresses = ["3Q2aKEGFTKDw3hghsBifXp39CZVMtZukxn","3Qnj4QtdD4qtZcP82xyLq2paAEPDgfwezd","1ELC1CgH9jcuQtzXRfmSNKtTiRYKqwjr9q"]
internals_ignore = ["1NETgKnhsGzpN3sW3vid6bhTjBM4o2iaKi","3AVhok6gvrgSbTXfGEksYKEWBdxS9qo8E6"]
ignore_because_refunded = ["1AKo8QK11FurGNvCnKuHvDFEFdavDRMZGo","16MmuQWvJuNqJRLgoYfwvBeqVHr3BqmFwk","13HfJkxPLJhTrCJEQSP3h6AZ23f63HpRGM"]

estimations = {}
loaded = {}
outgoing = []
incoming = []
ignored = []
failures = []

hash_to_script = {}

def ensure_is_present_with_offset(url, file_name, offset=0):
    global estimations
    global loaded
    global outgoing
    global incoming
    global ignored
    global failures

    innerfname = "offset-" + str(offset) + "-" + file_name
    exists = os.path.isfile(innerfname) 
    if not exists:
        time.sleep(2)
        u = urllib2.urlopen(url.replace("offset=0","offset="+str(offset)))
        f = open(innerfname, 'wb')
        meta = u.info()
        try:
            file_size = int(meta.getheaders("Content-Length")[0])
        except:
            file_size = 1000
        print "Downloading: %s Bytes: %s" % (innerfname, file_size)

        file_size_dl = 0
        block_sz = 8192
        while True:
            buffer = u.read(block_sz)
            if not buffer:
                break

            file_size_dl += len(buffer)
            f.write(buffer)
            status = r"%10d  [%3.2f%%]" % (file_size_dl, file_size_dl * 100. / file_size)
            status = status + chr(8)*(len(status)+1)
            print status,

        f.close()
        print ""
    with open(innerfname) as data_file:    
        data = json.load(data_file)
        if data["address"] not in estimations:
            estimations[data["address"]] = int(data["n_tx"])
            loaded[data["address"]] = 0
        if "txs" in data:
            loaded[data["address"]] += len(data["txs"])
        
        for x in data["txs"]:
            is_outgoing = False
            is_ignored = False

            hash_to_script[x["hash"]] = x["inputs"][0]["script"]

            for n in x["inputs"]:
                for uu in internals_ignore:
                    if n["prev_out"]["addr"] == uu:
                        is_ignored = True
                        break
                if n["prev_out"]["addr"] == data["address"]:
                    is_outgoing = True
                    break
                elif n["prev_out"]["addr"] == addresses[0] or n["prev_out"]["addr"] == addresses[1] or n["prev_out"]["addr"] == addresses[2]:
                    is_ignored = True
            if is_outgoing and not is_ignored:
                for pp in x["out"]:
                    if "addr" not in pp:
                        continue
                    for uu in internals_ignore:
                        if pp["addr"] == uu:
                            is_ignored = True
                            break

                    if pp["addr"] == data["address"]:
                        total_out += pp["value"]

                if is_ignored == True:
                    ignored.append(x)
                else:
                    outgoing.append(x)
            elif not is_outgoing and not is_ignored:
                # check if the output was 0.01BTC or higher
                is_failure = False
                total_out = 0
                for pp in x["out"]:
                    if "addr" not in pp:
                        continue
                    for uu in internals_ignore:
                        if pp["addr"] == uu:
                            is_ignored = True
                            break

                    if pp["addr"] == data["address"]:
                        total_out += pp["value"]

                if is_ignored == True:
                    ignored.append(x)
                elif total_out<1000000: # 1 mio satoshi = 0.01 BTC
                    failures.append(x)
                else:
                    incoming.append(x)
            else:
                ignored.append(x)


        # trigger next offset loading
        if len(data["txs"])==50:
            ensure_is_present_with_offset(url,file_name, offset+50)



# JSON Genesis Block
url = "https://raw.githubusercontent.com/elastic-project/genesis-block/master/genesis-block.json"
file_name = url.split('/')[-1]
ensure_is_present(url, file_name)



# All TX from 3Q2 address
url2 = "https://blockchain.info/address/3Q2aKEGFTKDw3hghsBifXp39CZVMtZukxn?format=json&offset=0&/3q2address.json"
file_name2 = url2.split('/')[-1]
ensure_is_present_with_offset(url2, file_name2)

# All TX from 3Qnj address
url3 = "https://blockchain.info/address/3Qnj4QtdD4qtZcP82xyLq2paAEPDgfwezd?format=json&offset=0&/3qnjaddress.json"
file_name3 = url3.split('/')[-1]
ensure_is_present_with_offset(url3, file_name3)

# All TX from 3Qnj address
url4 = "https://blockchain.info/address/1ELC1CgH9jcuQtzXRfmSNKtTiRYKqwjr9q?format=json&offset=0&/1elcjaddress.json"
file_name4 = url4.split('/')[-1]
ensure_is_present_with_offset(url4, file_name4)



# Manual Mappings
mappings = {}
#cryptodv
# 1Co9JKogApt7EfCr9n6GVxnEAL7nXT65Q2, signed "Cryptodv": HL5G1AGgEIMoSNChv5wgmOaSlBYULbNJQaVHAVPxc0Y1ZJcprYOn9QIj2kGrPpohwcgPy1KSuSNfdxV 
mappings["03f4592e1ec1ab1b57b13f0ace4d3da67b90a1eebfac32db6c2e7cfe042fdb5053"] = "0478e93b487a3e2ba63f496275a514587a6400eefb0c44a14117d6dd4200309a4c9a670bc5053dd1cc6696cc8300b92ad012f740e7ea7c1f288915a8048dbaec16"


# Did the loading go well?
print "Checking if the downloaded json files from blockchain.info are correct"
for x in estimations:
    print "Address %s\tshould have %d tx\tloaded %d tx" % (x,estimations[x],loaded[x])
    if estimations[x] != loaded[x]:
        print "!! FATAL ERROR, fix this first."
        sys.exit(1)
print "Total incoming tx:\t%d" % len(incoming)
print "Total outgoing tx:\t%d" % len(outgoing)
print "Total ignored tx:\t%d    (Those should be these, that move the funds from one address to another address)" % len(ignored)
for i in ignored:
    print "  ->",i["hash"]
print "Total fucked up tx:\t%d    (Those are these sending less than 0.01 BTC to the burn address)" % len(failures)
for i in failures:
    print "  ->",i["hash"]

print ""
print "Looking for suspicious activity (missing TX in genesis, too many TX in genesis, ...)"



def parse_real_tx(file_name):
    global all_inputs_plausible
    with open(file_name) as data_file:    
        data = json.load(data_file)
        if "txs" not in data:
            return
        we_want_this_many = int(data["n_tx"])
        data = data["txs"]
        print "%s TX history has %d entries, should have %d" % (file_name,len(data), we_want_this_many)
        print data[0]



print "Parsing JSON file"
import json
from pprint import pprint
abnormal = []

abnormal_addresses = []
normal_addresses = []
abnormal_addresses_xel = []
normal_addresses_xel = []

hashes_missing_in_history = []
btc_not_counted_in_genesis = 0.0
hashes_missing_in_genesis_block = []
loaded_hashes = []
override_cnt = 0
btc_normal = 0
with open(file_name) as data_file:    
    data = json.load(data_file)
    print "Genesis Block has %d entries" % len(data)
    for x in data:
        amount = float(x["mir_amount"])
        if amount <= 0:
            continue
        loaded_hashes.append(x["btc_tx"])
        pubkey = x["owner_pubkey"]
        if pubkey[0:2]=="02" or pubkey[0:2]=="03" or pubkey[0:2]=="04" and ' ' not in pubkey:
            # print "Pubkey [%s...] burned for [%.6f] XEL" % (pubkey[0:12], amount)
            btc_normal += float(x["btc_amount"])

            if pubkey in mappings:
                pubkey = mappings[pubkey]
                override_cnt = override_cnt + 1

            address = P2PKHBitcoinAddress.from_pubkey(pubkey.decode("hex"))
            a = str(address)
            normal_addresses.append(a)
            normal_addresses_xel.append(str(int(float(amount)*100000000)))
        else:
            abnormal.append(x)

missing_addresses = {}
missing_amounts_per_address = {}
missing_heights = {}

for x in incoming:
    missing_heights[x["hash"]] = str(x["block_height"]) 

    if x["hash"] not in loaded_hashes:
        hashes_missing_in_genesis_block.append(x["hash"])
        total_missing = 0.0
        for l in x["out"]:
            if "addr" in l and l["addr"] in addresses:
                total_missing += float(l["value"])
        missing_addresses[x["hash"]] = x["inputs"][0]["prev_out"]["addr"]
        if x["inputs"][0]["prev_out"]["addr"] not in missing_amounts_per_address:
            missing_amounts_per_address[x["inputs"][0]["prev_out"]["addr"]] = 0
        missing_amounts_per_address[x["inputs"][0]["prev_out"]["addr"]] += total_missing/100000000.0
        btc_not_counted_in_genesis += total_missing/100000000.0

for x in loaded_hashes:
    is_gone = True
    for ja in incoming:
        if ja["hash"]==x:
            is_gone = False
    if is_gone:
        hashes_missing_in_history.append(x["hash"])

print "Found %d suspicious tx" % (len(hashes_missing_in_genesis_block)+len(hashes_missing_in_history))
print "Hashes in blockchain but not in genesis block:"
for x in hashes_missing_in_genesis_block:
    print "  ->",x,"[" + missing_addresses[x] + "] =",missing_amounts_per_address[missing_addresses[x]],"BTC in total (dont count addrs twice)"
print "  =","total missing:",btc_not_counted_in_genesis,"BTC"
print "We had",override_cnt,"overrides of pubkeys"
print "Hashes in genesis block but never seen on blockchain:"



for x in hashes_missing_in_history:
    print "  ->",x
print "Trying to explain discrepancies ..."
print "Step 1: Looking for refunds"
explained = []
for x in missing_amounts_per_address:
    found = False
    outputs = {}
    total = 0.0
    for ou in outgoing:
        for xx in ou["out"]:
            if xx["addr"] == x:
                found = True
                outputs[ou["hash"]] = float(xx["value"])/100000000.0
                total += float(xx["value"])/100000000.0
    if len(outputs)==0:
        continue
    print "Explained missing %.6f BTC from %s, we saw refunds as high as %.6f BTC" % (missing_amounts_per_address[x],x,total)
    explained.append(x)

    for xy in outputs:
        print "  ->",xy,"refunded",outputs[xy],"BTC"

print "\nREBUILDING THE LIST OF MISSING ENTRIES IN GENESIS BLOCK!!\n"
tokill=[]
for k in hashes_missing_in_genesis_block:
    if missing_addresses[k] in explained:
        tokill.append(k)
for pao in tokill:
    hashes_missing_in_genesis_block.remove(pao)

def amtamt(height,amount):
    t = max(min(-(4000.0/25920.0)*(int(height)-400000)+8000,8000),0) * amount
    return str(t)
print "Found %d suspicious tx" % (len(hashes_missing_in_genesis_block)+len(hashes_missing_in_history))
print "Hashes in blockchain but not in genesis block **AND NOT EXPLAINED SO FAR**:"
for x in hashes_missing_in_genesis_block:
    if missing_addresses[x] in ignore_because_refunded:
        continue
    print "  ->",x," height:" + str(missing_heights[x]) + " XEL:" + amtamt(missing_heights[x],float(missing_amounts_per_address[missing_addresses[x]])) + " [" + missing_addresses[x] + "] =",missing_amounts_per_address[missing_addresses[x]],"BTC in total (dont count addrs twice)"





print "\n\nNow, Handing the Abnormal ..."
# Now handling the abnormals
btc_abnormal = 0
for x in abnormal:
    btc_abnormal += float(x["btc_amount"])
    scr = CScript(hash_to_script[x["btc_tx"]].decode("hex"))
    reas=""
    for kk in iter(scr):
        reas = kk
    scr2 = CScript(reas)
    reas=""
    want = 0
    have = 0
    cnt=0
    for kk in iter(scr2):
        if cnt==0:
            want=kk
        else:
            if len(str(kk))>30:
                reas += str(P2PKHBitcoinAddress.from_pubkey(kk)) + "-"
            else:
                have = kk
                normal_addresses.append(str(want)+"-"+reas[:-1])
                normal_addresses_xel.append(str(int(float(x["mir_amount"])*100000000)))
                break

        cnt = cnt + 1

    print " --> abnormal: ",want,"out of",have,"required",reas



print ""
print "STATISTICS"
print "=========="
print "In total, %.6f BTC came from abnormal transactions" % btc_abnormal
print "    -> %d are save 1-of-many multisig transactions" % 0
print "    -> %d are regular multisig transactions" % 0
print "    -> %d are cosigned by some creepy wallet" % 0
print "In total, %.6f BTC came from normal transactions" % btc_normal

print "\n\n"
t = 0
desired = 100000000.0
for ft in normal_addresses_xel:
    t += float(ft)
print "Total amount of XEL available:",t/100000000.0
print "Desired: ",desired
desired = desired * 100000000
print "[!!] scaing in background"
for i in range(len(normal_addresses_xel)):
    normal_addresses_xel[i] = long((float(normal_addresses_xel[i]) / t) * desired)
t = 0
for ft in normal_addresses_xel:
    t += float(ft)
print "Total scaled amount of XEL available:",t/100000000.0



addrset={}
for i in range(len(normal_addresses)):
    add = normal_addresses[i]
    if add not in addrset:
        addrset[add]=0
    addrset[add] += normal_addresses_xel[i]

normal_addresses = []
normal_addresses_xel = []

for key in addrset:
    normal_addresses.append(key)
    normal_addresses_xel.append(addrset[key])

total = 0
for i in range(len(normal_addresses_xel)):
    total += normal_addresses_xel[i]

print "\n\n\n"
print "ADDR ARRAY"
print ", ".join("\"" + d + "\"" for d in normal_addresses) 


print "\n\n\n"
print "AMOUNTS ARRAY"
print ", ".join(str(d) + "L" for d in normal_addresses_xel) 

#print "\n\nVerify:",total
#for i in range(len(normal_addresses)):
#    print normal_addresses[i] + "\t\t"+str(normal_addresses_xel[i]/100000000.0) + " XEL"