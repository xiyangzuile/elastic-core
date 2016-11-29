package nxt;

import java.util.HashMap;

class DoubleLongNode{
    long key1;
    long key2;
    long value;
    DoubleLongNode pre;
    DoubleLongNode next;
 
    public DoubleLongNode(long key1, long key2, long value){
        this.key1 = key1;
        this.key2 = key2;
        this.value = value;
    }
}

public class DoubleLongLRUCache {
    int capacity;
    HashMap<Pair<Long,Long>, DoubleLongNode> map = new HashMap<Pair<Long,Long>, DoubleLongNode>();
    DoubleLongNode head=null;
    DoubleLongNode end=null;
 
    public DoubleLongLRUCache(int capacity) {
        this.capacity = capacity;
    }
 
    public long get(long key1, long key2) {
    	Pair<Long,Long> pairkey = new Pair<Long,Long>(key1,key2);
        if(map.containsKey(pairkey)){
        	DoubleLongNode n = map.get(pairkey);
            remove(n);
            setHead(n);
            return n.value;
        }
 
        return -1;
    }
 
    public void remove(DoubleLongNode n){
        if(n.pre!=null){
            n.pre.next = n.next;
        }else{
            head = n.next;
        }
 
        if(n.next!=null){
            n.next.pre = n.pre;
        }else{
            end = n.pre;
        }
 
    }
 
    public void setHead(DoubleLongNode n){
        n.next = head;
        n.pre = null;
 
        if(head!=null)
            head.pre = n;
 
        head = n;
 
        if(end ==null)
            end = head;
    }
 
    public void set(long key1, long key2, long value) {
    	Pair<Long,Long> pairkey = new Pair<Long,Long>(key1,key2);
        if(map.containsKey(pairkey)){
            DoubleLongNode old = map.get(pairkey);
            old.value = value;
            remove(old);
            setHead(old);
        }else{
        	DoubleLongNode created = new DoubleLongNode(key1, key2, value);
            if(map.size()>=capacity){
                map.remove(pairkey);
                remove(end);
                setHead(created);
 
            }else{
                setHead(created);
            }    
            map.put(pairkey, created);
        }
    }
    
    public void increment(long key1, long key2) {
    	long res = this.get(key1, key2);
    	res = res + 1;
    	this.set(key1, key2, res);
    }
}