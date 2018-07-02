package ctbrec.ui;



import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class DonateTabFx extends Tab {

    public DonateTabFx() {
        setClosable(false);
        setText("Donate");
        BorderPane container = new BorderPane();
        container.setPadding(new Insets(10));
        container.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, new Insets(0))));
        setContent(container);

        VBox headerVbox = new VBox(10);
        headerVbox.setAlignment(Pos.CENTER);
        Label beer = new Label("Buy me some beer?!");
        beer.setFont(new Font(36));
        Label desc = new Label("If you like this software and want to buy me some beer or pizza, here are some possibilities!");
        desc.setFont(new Font(24));
        headerVbox.getChildren().addAll(beer, desc);
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER);
        header.getChildren().add(headerVbox);
        header.setPadding(new Insets(20, 0, 0, 0));
        container.setTop(header);

        ImageView coffeeImage = new ImageView(getClass().getResource("/html/buymeacoffee-fancy.png").toString());
        Button coffeeButton = new Button("Buy me a coffee");
        coffeeButton.setOnMouseClicked((e) -> { Launcher.open("https://www.buymeacoffee.com/0xboobface"); });
        VBox buyCoffeeBox = new VBox(5);
        buyCoffeeBox.setAlignment(Pos.TOP_CENTER);
        buyCoffeeBox.getChildren().addAll(coffeeImage, coffeeButton);

        int prefWidth = 360;
        TextField bitcoinAddress = new TextField("15sLWZon8diPqAX4UdPQU1DcaPuvZs2GgA");
        bitcoinAddress.setEditable(false);
        bitcoinAddress.setPrefWidth(prefWidth);
        ImageView bitcoinQrCode = new ImageView(getClass().getResource("/html/bitcoin-address.png").toString());
        Label bitcoinLabel = new Label("Bitcoin");
        bitcoinLabel.setGraphic(new ImageView(getClass().getResource("/html/bitcoin.png").toString()));
        VBox bitcoinBox = new VBox(5);
        bitcoinBox.setAlignment(Pos.TOP_CENTER);
        bitcoinBox.getChildren().addAll(bitcoinLabel, bitcoinAddress, bitcoinQrCode);

        TextField ethereumAddress = new TextField("0x996041638eEAE7E31f39Ef6e82068d69bA7C090e");
        ethereumAddress.setEditable(false);
        ethereumAddress.setPrefWidth(prefWidth);
        ImageView ethereumQrCode = new ImageView(getClass().getResource("/html/ethereum-address.png").toString());
        Label ethereumLabel = new Label("Ethereum");
        ethereumLabel.setGraphic(new ImageView(getClass().getResource("/html/ethereum.png").toString()));
        VBox ethereumBox = new VBox(5);
        ethereumBox.setAlignment(Pos.TOP_CENTER);
        ethereumBox.getChildren().addAll(ethereumLabel, ethereumAddress, ethereumQrCode);

        TextField moneroAddress = new TextField("448ZQZpzvT4iRNAVBr7CMQBfEbN3H8uAF2BWabtqVRckgTY3GQJkUgydjotEPaGvpzJboUpe39J8rPBkWZaUbrQa31FoSMj");
        moneroAddress.setEditable(false);
        moneroAddress.setPrefWidth(prefWidth);
        ImageView moneroQrCode = new ImageView(getClass().getResource("/html/monero-address.png").toString());
        Label moneroLabel = new Label("Monero");
        moneroLabel.setGraphic(new ImageView(getClass().getResource("/html/monero.png").toString()));
        VBox moneroBox = new VBox(5);
        moneroBox.setAlignment(Pos.TOP_CENTER);
        moneroBox.getChildren().addAll(moneroLabel, moneroAddress, moneroQrCode);

        HBox coinBox = new HBox(5);
        coinBox.setAlignment(Pos.CENTER);
        coinBox.setSpacing(50);
        coinBox.getChildren().addAll(bitcoinBox, ethereumBox, moneroBox);

        VBox centerBox = new VBox(50);
        centerBox.getChildren().addAll(buyCoffeeBox, coinBox);
        container.setCenter(centerBox);
    }
}
