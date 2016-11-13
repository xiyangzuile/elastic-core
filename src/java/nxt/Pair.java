package nxt;

public class Pair<T, U>
{
   T a;
   U b;

   Pair(T a, U b)
   {
    this.a = a;
    this.b = b;
   }

   public T getA(){ return a;}
   public U getB(){ return b;}
}
