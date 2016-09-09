package ElasticPL;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;

class Pair<F, S> {
    private F first; //first member of pair
    private S second; //second member of pair

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public void setFirst(F first) {
        this.first = first;
    }

    public void setSecond(S second) {
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }
}

public class RuntimeEstimator {
	

  public static long worstWeight(SimpleNode node){
    Stack<SimpleNode> _stack = new Stack<SimpleNode>();
    Map<Integer, Long> partial_weights = new HashMap<Integer, Long>();    
    Map<Integer, Integer> repeat_memory = new HashMap<Integer, Integer>();  
    int nested=0;
    _stack.push(node);
    while (_stack.size() > 0) {
        SimpleNode temp = _stack.pop();
        if (!partial_weights.containsKey(nested)){
              partial_weights.put(nested, 0L);
        }
        partial_weights.put(nested, (Long)partial_weights.get(nested) + temp.weight());         
        if (temp.marker()){
            MarkerNode casted = (MarkerNode)temp;
            if (casted.get_marker_type() == MarkerNode.MarkerType.IF_STATEMENT_BEGIN || casted.get_marker_type() == MarkerNode.MarkerType.WHILE_STATEMENT_BEGIN){
              nested = nested + 1;
            }
            if (casted.get_marker_type() == MarkerNode.MarkerType.IF_STATEMENT_DIVIDER){
              // Swap nested key in map to negative
              Long s = 0L;
              if (partial_weights.containsKey(nested)){
                s = (Long)partial_weights.get(nested);
                partial_weights.remove(nested);
              }
              partial_weights.put(-1*nested, s);
            }
            if (casted.get_marker_type() == MarkerNode.MarkerType.IF_STATEMENT_END){
              // Let only the best survive, also decrement nested
              Long s1 = 0L;
              if (partial_weights.containsKey(nested)){
                s1 = (Long)partial_weights.get(nested);
                partial_weights.remove(nested);
              }
              Long s2 = 0L;
              if (partial_weights.containsKey(-1*nested)){
                s2 = (Long)partial_weights.get(-1*nested);
                partial_weights.remove(-1*nested);
              }
              Long max = (s1>s2)?s1:s2;
              //partial_weights.put(nested, max);
              nested=nested-1;
              Long s = (Long)partial_weights.get(nested);
              partial_weights.remove(nested);
              partial_weights.put(nested, s+max);
            }
            if (casted.get_marker_type() == MarkerNode.MarkerType.WHILE_STATEMENT_END){
              // Let only the best survive, also decrement nested
              Long s1 = 0L;
              if (partial_weights.containsKey(nested)){
                s1 = (Long)partial_weights.get(nested);
                partial_weights.remove(nested);
              }
              nested=nested-1;
              Integer rpt = 0;
              if (repeat_memory.containsKey(nested)){
                rpt = (Integer)repeat_memory.get(nested);
                repeat_memory.remove(nested);
              }
              Long s = (Long)partial_weights.get(nested);
              partial_weights.remove(nested);
              partial_weights.put(nested, s+(rpt*s1));
            }

        }else if(temp.conditional()){
            MarkerNode marker1 = new MarkerNode(0);
            marker1.set_marker_type(MarkerNode.MarkerType.IF_STATEMENT_BEGIN);
            MarkerNode marker2 = new MarkerNode(0);
            marker2.set_marker_type(MarkerNode.MarkerType.IF_STATEMENT_DIVIDER);
            MarkerNode marker3 = new MarkerNode(0);
            marker3.set_marker_type(MarkerNode.MarkerType.IF_STATEMENT_END);
            _stack.push((SimpleNode)temp.children[0]); // if-condition
            _stack.push(marker3); // end marker
            _stack.push((SimpleNode)temp.children[1]); // first branch
            _stack.push(marker2); // branch divider
            _stack.push((SimpleNode)temp.children[2]); // second branch
            _stack.push(marker1); // indicator that we begin differing between two branches
        }else if(temp.repeat()){
            repeat_memory.put(nested, ((ASTRepeatStatement)temp).repeat_times);
            MarkerNode marker1 = new MarkerNode(0);
            marker1.set_marker_type(MarkerNode.MarkerType.WHILE_STATEMENT_BEGIN);
            MarkerNode marker2 = new MarkerNode(0);
            marker2.set_marker_type(MarkerNode.MarkerType.WHILE_STATEMENT_END);
            _stack.push(marker2); // end marker
            _stack.push((SimpleNode)temp.children[0]); // first branch
            _stack.push(marker1); // branch divider
        }else if(!temp.ignore() && temp.children!=null){
            for(int i=0;i<temp.children.length;++i){
              SimpleNode sn = (SimpleNode)temp.children[i];
              if(sn.ignore()==false)
                _stack.push(sn);
            }
        }
    }
    Long sum=0L;
    for (Map.Entry<Integer, Long> entry : partial_weights.entrySet())
    {
        sum+=entry.getValue();
    }
    return sum;
  }

}
