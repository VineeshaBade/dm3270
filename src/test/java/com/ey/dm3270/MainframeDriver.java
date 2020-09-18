package com.ey.dm3270;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.bytezone.dm3270.UnlockWaiter;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.ConnectionListener;
import com.bytezone.dm3270.TerminalClient;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.ScreenDimensions;

public class MainframeDriver{
    static final Logger LOG = LoggerFactory.getLogger(MainframeDriver.class);
//    static final Log logger = LogFactory.getLog(MainframeDriver.class);
    /*********************************************************************************
     * Mainframe Settings
     ********************************************************************************/
    public TerminalClient client;
    private final String SERVICE_HOST = "INSERT HOST URL HERE";
    private final int PORT = 1;
    private final int TERMINAL_MODEL_TYPE_TWO = 2;
    private final ScreenDimensions SCREEN_DIMENSIONS = new ScreenDimensions(24, 80);
    // ********************************************************************************

    private final long TIMEOUT_MILLIS = 10000;
    private ExceptionWaiter exceptionWaiter;
    private ScheduledExecutorService stableTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    public MainframeDriver() {
        try {
            setUpSSLDriver();
        } catch (GeneralSecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public MainframeDriver(TerminalClient client) {
        this.client = client;
    }

    public void setUpSSLDriver() throws GeneralSecurityException {
        System.setProperty("javax.net.ssl.keyStore", getResourceFilePath("/keystore.jks"));
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        client = new TerminalClient(TERMINAL_MODEL_TYPE_TWO, SCREEN_DIMENSIONS);
        client.setSocketFactory(buildSslContext().getSocketFactory());
        connectClient();
    }

    private SSLContext buildSslContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManager trustManager = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        };
        sslContext.init(null, new TrustManager[] { trustManager }, new SecureRandom());
        return sslContext;
    }

    public String getResourceFilePath(String resourcePath) {
        return getClass().getResource(resourcePath).getFile();
    }

    private void connectClient() {
        client.connect(SERVICE_HOST, PORT);
		client.addScreenChangeListener(screenWatcher -> LOG.debug("Screen updated, cursor={}, alarm={}, screen:{}",
				client.getCursorPosition().orElse(null), client.isAlarmOn(), getScreenText()));
//        client.addScreenChangeListener(screenWatcher -> logger.info("Screen updated:" + "\n" + getScreenText()));
    }

    private String getScreenText() {
        return client.getScreenText().replace('\u0000', ' ');
    }

    public TerminalClient getClient() {
        return client;
    }

    void awaitKeyboardUnlock() throws InterruptedException, TimeoutException {
        new UnlockWaiter(client, stableTimeoutExecutor).await(TIMEOUT_MILLIS);
    }

    private class ExceptionWaiter implements ConnectionListener {

        private CountDownLatch exceptionLatch = new CountDownLatch(1);

        private CountDownLatch closeLatch = new CountDownLatch(1);

        @Override
        public void onConnection() {
        }

        @Override
        public void onException(Exception ex) {
            exceptionLatch.countDown();
        }

        @Override
        public void onConnectionClosed() {
            closeLatch.countDown();
        }

    }

//    public void setFieldTextBy(String field, String text) throws IOException {
    public void setFieldTextBy(String[] attributeAndValue, String text) throws IOException {
//        CommonUtils utils = new CommonUtils();
//        String[] attributeAndValue = utils.readObjectRepo(field).split("\\.");
        if (attributeAndValue[0].equalsIgnoreCase("field")) {
            String[] coordinates = attributeAndValue[0].split(",");
            client.setFieldTextByCoord(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), text);

        } else if (attributeAndValue[0].equalsIgnoreCase("label")) {
            client.setFieldTextByLabel(attributeAndValue[1], text);
        }
    }

    public void sendKey(String key) {
        String FINAL_KEY = key.toUpperCase();
        switch (FINAL_KEY) {
            case "ENTER":
                client.sendAID(AIDCommand.AID_ENTER, FINAL_KEY);
                break;
            case "CLEAR":
                client.sendAID(AIDCommand.AID_CLEAR, FINAL_KEY);
                break;
            case "F3":
                client.sendAID(AIDCommand.AID_PF3, "P" + FINAL_KEY);
                break;
            case "F8":
                client.sendAID(AIDCommand.AID_PF8, "P" + FINAL_KEY);
                break;
            case "F13":
                client.sendAID(AIDCommand.AID_PF13, "P" + FINAL_KEY);
                break;
        }

    }

    public String printScreenToConsole() {
        System.err.println(client.getScreenText());
        return client.getScreenText();
    }

    public void sendFieldByTab(String text, int offset) throws NoSuchFieldException {
        client.setTabulatedInput(text, offset);
    }

    private final HashMap<RenderingHints.Key, Object> renderingProperties = new HashMap<>();

    //	public byte[] createScreenshot(String screen) throws IOException {
    public BufferedImage createScreenshot(String screen) throws IOException {

        String screenText = StringUtils.join(screen, "\n");

        renderingProperties.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        renderingProperties.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        renderingProperties.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        Font font = new Font("Consolas", Font.PLAIN, 12);
        FontRenderContext fontRenderContext = new FontRenderContext(null, true, true);
        BufferedImage bufferedImage = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = bufferedImage.createGraphics();
        graphics2D.setRenderingHints(renderingProperties);
        graphics2D.setBackground(Color.black);
        graphics2D.setColor(Color.GREEN);
        graphics2D.setFont(font);
        graphics2D.clearRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());

        TextLayout textLayout = new TextLayout(screenText, font, fontRenderContext);

        int count = 0;
        for (String line : screenText.split("\n")) {
            graphics2D.drawString(line, 15, (int) (15 + count * textLayout.getBounds().getHeight() + 0.5));
            count++;
        }
        graphics2D.dispose();

//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        ImageIO.write(bufferedImage, "PNG", out);
//        ImageIO.write(bufferedImage, "png", new File("TestingScreenshot.png"));
//        return out.toByteArray();
        return bufferedImage;
    }

    private String encodeFileToBase64Binary(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        baos.flush();
        byte[] imageInByte = baos.toByteArray();
        baos.close();
        return new String(Base64.encodeBase64(imageInByte));
    }

    public String getTextByIndex(int index) {
        List<Field> fields = client.getFields();
        return fields.get(index).getText();
    }

    public String getTextByLabel(String label) {
        //TODO: can improve performance by accessing the fields methods directly. Improve @ EY
        String text="not found";
        for(Field field:client.getFields()) {
            if(field.getText().equalsIgnoreCase(label)) {
                text = field.getNextUnprotectedField().getText();
            }
        }
        return text;
    }

    public void printFieldsAndIndexes() {
        int i=0;
        for(Field field:client.getFields()) {
            System.err.println(i+". "+field.getText());
            i++;
        }
    }

}

