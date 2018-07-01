package ctbrec;

import static ctbrec.ui.Launcher.BASE_URI;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.ui.HtmlParser;

public class ModelParser {
    private static final transient Logger LOG = LoggerFactory.getLogger(ModelParser.class);

    public static List<Model> parseModels(String html) {
        List<Model> models = new ArrayList<>();
        Elements cells = HtmlParser.getTags(html, "ul.list > li");
        for (Element cell : cells) {
            String cellHtml = cell.html();
            try {
                Model model = new Model();
                model.setName(HtmlParser.getText(cellHtml, "div.title > a").trim());
                model.setPreview(HtmlParser.getTag(cellHtml, "a img").attr("src"));
                model.setUrl(BASE_URI + HtmlParser.getTag(cellHtml, "a").attr("href"));
                model.setDescription(HtmlParser.getText(cellHtml, "div.details ul.subject"));
                Elements tags = HtmlParser.getTags(cellHtml, "div.details ul.subject li a");
                if(tags != null) {
                    for (Element tag : tags) {
                        model.getTags().add(tag.text());
                    }
                }
                models.add(model);
            } catch (Exception e) {
                LOG.error("Parsing of model details failed: {}", cellHtml, e);
            }
        }
        return models;
    }
}
