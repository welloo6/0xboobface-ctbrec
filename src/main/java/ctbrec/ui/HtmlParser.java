package ctbrec.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class HtmlParser {

    /**
     * Returns the tag selected by the given selector or null
     *
     * @param html
     * @param charset
     * @param cssSelector
     * @return the tag selected by the given selector or null
     */
    public static Element getTag(String html, String cssSelector) {
        Elements selection = getTags(html, cssSelector);
        if (selection.size() == 0) {
            throw new RuntimeException("Bad selector. No element selected by " + cssSelector);
        }
        Element tag = selection.first();
        return tag;
    }

    public static Elements getTags(String html, String cssSelector) {
        Document doc = Jsoup.parse(html);
        return doc.select(cssSelector);
    }

    /**
     *
     * @param html
     * @param charset
     * @param cssSelector
     * @return The text content of the selected element or an empty string, if nothing has been selected
     */
    public static String getText(String html, String cssSelector) {
        Document doc = Jsoup.parse(html);
        Elements selection = doc.select(cssSelector);
        if (selection.size() == 0) {
            throw new RuntimeException("Bad selector. No element selected by " + cssSelector);
        }
        Element elem = selection.first();
        return elem.text();
    }
}
