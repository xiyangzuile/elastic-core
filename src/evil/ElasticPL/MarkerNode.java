package ElasticPL;
public
class MarkerNode extends SimpleNode implements Node {

  public MarkerNode(int id) {
    super(id);
  }

  public int marker_indicator(){
    return int_marker_indicator;
  }

  public void set_marker_indicator(int m){
    this.int_marker_indicator = m;
  }

  public boolean marker(){
    return true;
  }

  private int int_marker_indicator = 0;

}

