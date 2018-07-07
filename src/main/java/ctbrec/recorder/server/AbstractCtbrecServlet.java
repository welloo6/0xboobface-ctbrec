package ctbrec.recorder.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import ctbrec.Config;
import ctbrec.Hmac;

public abstract class AbstractCtbrecServlet extends HttpServlet {

    boolean checkAuthentication(HttpServletRequest req, String body) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        boolean authenticated = false;
        if(Config.getInstance().getSettings().key != null) {
            String reqParamHmac = req.getParameter("hmac");
            String httpHeaderHmac = req.getHeader("CTBREC-HMAC");
            String hmac = null;
            if(reqParamHmac != null) {
                hmac = reqParamHmac;
            }
            if(httpHeaderHmac != null) {
                hmac = httpHeaderHmac;
            }

            byte[] key = Config.getInstance().getSettings().key;
            authenticated = Hmac.validate(body, key, hmac);
        } else {
            authenticated = true;
        }
        return authenticated;
    }


    String body(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        BufferedReader br = req.getReader();
        String line= null;
        while( (line = br.readLine()) != null ) {
            body.append(line).append("\n");
        }
        return body.toString().trim();
    }
}
