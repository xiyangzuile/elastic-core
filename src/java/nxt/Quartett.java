package nxt;

public class Quartett<T, U, V, W>
{
   T a;
   U b;
   V c;
   W d;

   Quartett(T a, U b, V c, W d)
   {
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
   }

   public T getA(){ return a;}
   public U getB(){ return b;}
   public V getC(){ return c;}
   public W getD(){ return d;}
}
