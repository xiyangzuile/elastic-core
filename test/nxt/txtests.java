package nxt;

import nxt.util.Convert;
import org.junit.Test;

/**
 * Created by anonymous on 19.03.17.
 */
public class txtests {

    @Test
    public void TXDecode() throws Exception {
        String hex1 = "0312aed45d060300ffbc7ba2e4c43be03f8a7f020d0651f582ad1901c254eebb4ec2ecb73148e50ddd6519e3a41b2319e3fc3ead898f1fc500f82cce681e172bf0d052ebd79bb47a6ba66909eaf365ed0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008ebec6349fbebf12ed5a047b137d018e93fd157a92814127640ddb17240f8906c1fc8618f66d8f6b5811bd9e4cc9c21f2193e6f08d820b8311e01f97584afb90ceb4d748512d2a2b7c1919cb0cfe71091fa731226cee5480b6f2192b0a68060a25662b0e796cf886d4ceb6eae8e6ac1ac8862493710b09ce33eef824f21100820000000000000000618e19af1ec34ffa01cad756f49d30e9d10500000000000000a1730f020000000000000000000000006b2595970e089662";
        byte[] txbyte = Convert.parseHexString(hex1);
        TransactionImpl t1 = TransactionImpl.newTransactionBuilder(txbyte).build();
        System.out.println(t1.toString());
    }
}
