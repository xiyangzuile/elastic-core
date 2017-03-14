package nxt.util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPValidator{
    private static IPValidator instance = null;
    private Pattern pattern;
    private Matcher matcher;

    private static final String IPADDRESS_PATTERN =
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    private IPValidator(){
        pattern = Pattern.compile(IPADDRESS_PATTERN);
    }
    public static synchronized IPValidator getInstance () {
        if (IPValidator.instance == null) {
            IPValidator.instance = new IPValidator ();
        }
        return IPValidator.instance;
    }

    /**
     * Validate ip address with regular expression
     * @param ip ip address for validation
     * @return true valid ip address, false invalid ip address
     */
    public boolean validate(final String ip){
        matcher = pattern.matcher(ip);
        return matcher.matches();
    }
}