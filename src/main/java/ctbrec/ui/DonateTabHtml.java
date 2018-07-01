package ctbrec.ui;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.Tab;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class DonateTabHtml extends Tab {

    private static final transient Logger LOG = LoggerFactory.getLogger(DonateTabHtml.class);

    private WebView browser;

    public DonateTabHtml() {
        setClosable(false);
        setText("Donate");

        browser = new WebView();
        try {
            WebEngine webEngine = browser.getEngine();
            URL donatePage = getClass().getResource("/html/donate.html");
            webEngine.load(donatePage.toString());
            webEngine.setJavaScriptEnabled(true);
            webEngine.setOnAlert((e) -> {
                System.out.println(e.getData());
            });
            setContent(browser);
        } catch (Exception e) {
            LOG.error("Couldn't load donate.html", e);
        }
    }
}
