package datamea.backend;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Email {

    //------------------Declaring Variables------------------//
    private double VNEGTHRESH;
    private double NEGTHRESH;
    private double NEUTHRESH;
    private double POSTHRESH;
    private ArrayList<String> sentences;
    private Message           message;
    Sentiment                 sentenceSentiment;
    private int[]             sentimentScores;
    private int               sentencesAnalyzed;
    private double            sentimentPct;
    private String            content, title, sentimentPctStr;
    private Date              date;
    private Sender            sender;
    private Flags             flags;
    private static int        VNEG;
    private static int        NEG;
    private static int        NEU;
    private static int        POS;
    private static int        VPOS;
    private static int        VMULT;
    private int               MAXLEN;
    private int               MINLEN;
    private String            folder;
    private String            subFolder;
    private ArrayList<String> attachments;
    File                      serializedEmail;
    private int               dayOfWeek;
    private String            language;
    private ArrayList<String> recipients;


    public Email(File f) {
        //to do: recreate emails using this constructor
        attachments = new ArrayList<>();
        recipients = new ArrayList<>();
        sentimentScores = new int[5];
        recoverEmail(f);


        VNEGTHRESH = .7;
        NEGTHRESH = .65;
        NEUTHRESH = .5;
        POSTHRESH = .3;
        VNEG = 0;
        NEG = 1;
        NEU = 2;
        POS = 3;
        VPOS = 4;
        VMULT = 3;
        MAXLEN = 300;
        MINLEN = 10;

    }

    public void recoverEmail(File f) {
        BufferedReader br;

        try {
            br = new BufferedReader(new FileReader(f));
            this.folder = User.decrypt(br.readLine());
            this.subFolder = User.decrypt(br.readLine());
            long unixDate = Long.parseLong(br.readLine());
            this.date = new Date(unixDate);
            this.sender = new Sender(User.decrypt(br.readLine()));
            this.flags = new Flags(br.readLine());

            //add fields to reconstruct sentiment analysis


            this.sentimentScores[0] = Integer.parseInt(br.readLine());
            this.sentimentScores[1] = Integer.parseInt(br.readLine());
            this.sentimentScores[2] = Integer.parseInt(br.readLine());
            this.sentimentScores[3] = Integer.parseInt(br.readLine());
            this.sentimentScores[4] = Integer.parseInt(br.readLine());

            String atts = br.readLine();
            if(atts.length() > 2) {
                String[] attsAry = ((atts.replace("[", "")).replace("]", "")).split(",");
                if (attsAry.length > 0)
                    this.attachments.addAll(Arrays.asList(attsAry));
            }

            this.language = br.readLine();

            int rCount = Integer.parseInt(br.readLine());

            for(int i = 0; i < rCount; i ++){
                recipients.add(User.decrypt(br.readLine()));
            }

            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("this email cannot be properly processed... Invalid form");
            System.out.println(f.getName());
            f.delete();
        }

    }


    public Email(Message m, Sender s, Boolean rs) {

        VNEGTHRESH = .7;
        NEGTHRESH = .65;
        NEUTHRESH = .5;
        POSTHRESH = .3;
        VNEG = 0;
        NEG = 1;
        NEU = 2;
        POS = 3;
        VPOS = 4;
        VMULT = 3;
        message = m;
        sentimentScores = new int[5];
        recipients = new ArrayList<>();
        sentencesAnalyzed = 0;
        MAXLEN = 300;
        MINLEN = 10;

        boolean runSentiment = rs;
        try {
            //System.out.println("Content: \n" + m.getContent().toString());

            title = m.getSubject();
            attachments = extractAttachments();
            sender = s;
            date = m.getSentDate();
            flags = m.getFlags();


        } catch (Exception e) {
            e.printStackTrace();
        }

        if (runSentiment) {
            try {
                content = getTextFromMessage(m);
            } catch (IOException | MessagingException e) {
                e.printStackTrace();
            }

            if (content != null && !content.equals("")) {
                language = detectLanguage(content);
                sentences = getSentences(content);

            }

            //System.out.println(sentences.toString());
            initializeSentiment();
        }
    }

    private void initializeSentiment() {
        int sentenceScore;
        double probability;
        Sentiment sentenceSentiment;
        if (sentences != null) {
            if(Pipeline.pipeline() == null)
                Pipeline.initPipeline();
            for (String sentence : sentences) {
                if (getLanguage() != null && !getLanguage().equals("")) {
                    if (this.getLanguage().equals("en") && sentence.length() < MAXLEN && sentence.length() > MINLEN) {
                        //System.out.println("sentence being analyzed: " + sentence);
                        sentencesAnalyzed++;
                        sentenceSentiment = analyzeSentiment(sentence);
                        sentenceScore = sentenceSentiment.score;
                        probability = sentenceSentiment.probability;
                        switch (sentenceScore) {
                            case 0:
                                //System.out.println(probability + " chance it is Very Negative");
                                if (probability > VNEGTHRESH) {
                                    this.sentimentScores[VNEG]++;
                                    //System.out.println("Incrementing Very Negative");
                                } else {
                                    this.sentimentScores[NEG]++;
                                    //System.out.println("Incrementing Negative");
                                }
                                break;
                            case 1:
                                //System.out.println(probability + " chance it is Negative");
                                if (probability > NEGTHRESH) {
                                    this.sentimentScores[NEG]++;
                                    //System.out.println("Incrementing Negative");
                                } else {
                                    this.sentimentScores[NEU]++;
                                    //System.out.println("Incrementing Neutral");
                                }
                                break;
                            case 2:
                                //System.out.println(probability + " chance it is Neutral");
                                if (probability > NEUTHRESH) {
                                    this.sentimentScores[NEU]++;
                                    //System.out.println("Incrementing Neutral");
                                } else {
                                    this.sentimentScores[POS]++;
                                    //System.out.println("Incrementing Positive");
                                }
                                break;
                            case 3:
                                //System.out.println(probability + " chance it is Positive");
                                if (probability > POSTHRESH) {
                                    this.sentimentScores[POS]++;
                                    //System.out.println("Incrementing Positive");
                                } else {
                                    this.sentimentScores[VPOS]++;
                                    //System.out.println("Incrementing Very Positive");
                                }
                                break;
                            case 4:
                                //System.out.println(probability + " chance it is Very Positive");
                                this.sentimentScores[4]++;
                                //System.out.println("Incrementing Very Positive");
                                break;
                            default:
                                break;

                        }
                    }
                }

                sentimentPct = getOverallSentimentDbl(sentimentScores);
            }
        }
    }


    public static double getOverallSentimentDbl(int [] sentimentScores) {
        int sentencesAnalyzed = sentimentScores[0] + sentimentScores[1] + sentimentScores[2] + sentimentScores[3] + sentimentScores[4];
        double sentimentDbl;

        int overallSentiment = sentimentScores[VPOS] * VMULT + sentimentScores[POS] -
                sentimentScores[NEG] - sentimentScores[VNEG] * VMULT;
        

        if (sentencesAnalyzed > 0) {
            sentimentDbl = ((((double) overallSentiment / sentencesAnalyzed) * 100) / 2) + 50;
        }
        else sentimentDbl = 0;

        return sentimentDbl;


    }

    /*
This function was modified from an existing function by ItsCuties from the site below
http://www.itcuties.com/java/javamail-read-email/
Some additional notes:
-whenever something like javax.mail.internet.MimeMultipart@396f6598 appears as the message content,
it appears to be whenever there is a thread of replies
-anything (from what i've checked) that is html, is a mass email
 */

    private String getTextFromMessage(Message message) throws IOException, MessagingException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
            boolean msgExists = sender.addMessage(result.hashCode());
            if(msgExists)
                result = "";
        } else if (message.isMimeType("multipart/*")) {
            result = getTextFromMimeMultipart((MimeMultipart) message.getContent());
        }
        return result;
    }

    private String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart) throws IOException, MessagingException {

        int count = mimeMultipart.getCount();
        if (count == 0)
            throw new MessagingException("Multipart with no body parts not supported.");

        String result = "";
        for (int i = 0; i < count; i++) {
            result += getTextFromBodyPart(mimeMultipart.getBodyPart(i));
        }
        return result;
    }

    private String getTextFromBodyPart(
            BodyPart bodyPart) throws IOException, MessagingException {

        String result = "";
        if (bodyPart.isMimeType("text/plain")) {
            result = (String) bodyPart.getContent();
            if(sender.addMessage(result.hashCode()))
                result = "";
        } else if (bodyPart.isMimeType("text/html")) {
            result = org.jsoup.Jsoup.parse((String) bodyPart.getContent()).text();
            if(sender.addMessage(result.hashCode()))
                result = "";
        } else if (bodyPart.getContent() instanceof MimeMultipart){
            result = getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
        }
        return result;
    }

    private ArrayList<String> getSentences(String result) {

        ArrayList<String> sentences = new ArrayList<>();
        String[] split = (filter(result)).split("~|\\n");
        for (String s : split) {
            if (s.length() > 0) {
                String trimmed = s.trim();
                char[] c = trimmed.toCharArray();
                if (c.length > 0) {
                    if (c[0] >= 65 && c[0] <= 90 &&
                            (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")))
                        sentences.add(trimmed);
                }
            }
        }

        return sentences;
    }


    static Sentiment analyzeSentiment(String message) {
        //System.out.println("Processing annotation");
        Annotation annotation = Pipeline.pipeline().process(message);
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

        int sentimentScore = -1;
        double probability = -1;

        for (CoreMap s : sentences) {
            Tree tree = s.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
            sentimentScore = RNNCoreAnnotations.getPredictedClass(new CoreLabel(tree.label()));
            probability = RNNCoreAnnotations.getPredictedClassProb(new CoreLabel(tree.label()));
        }


        Sentiment sentiment = new Sentiment(sentimentScore, probability);

        return sentiment;
    }


    public static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    public static String filter(String text) {

        int ABBR = 14;
        String newText = text;

        String[][] abbreviations = {{"(^|-)(d|D)r\\.", "(^|-)(M|m)r\\.", "(^|-)(M|m)rs\\.", "(^|-)(P|p)rof\\.",
                "^(J|j)an\\.", "^(F|f)eb\\.", "^(M|m)ar\\.", "^(A|a)pr\\.", "^(J|j)un\\.",
                "^(A|a)ug\\.", "^(S|s)ep\\.", "^(O|o)ct\\.", "^(N|n)ov\\.", "^(D|d)ec\\.", "p&nbsp"},
                {"Dr", "Mr", "Mrs", "Professor", "January", "Februrary", "March", "April", "June", "August",
                        "September", "October", "November", "December", " "}};

        for (int i = 0; i < ABBR; i++) {
            newText = newText.replaceAll(abbreviations[0][i], abbreviations[1][i]);
        }

        newText = newText.replaceAll("(http|https|ftp|ftps)\\:\\/\\/[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(\\/\\S*)?", "") //urls
                .replaceAll("^([a-z0-9_\\.-]+)@(?!domain.com)([\\da-z\\.-]+)\\.([a-z\\.]{2,6})$", "")//email addrs
                .replaceAll("[`,~,*,#,^,>,\\-,\\n,\\t,\\r]", "")//unwanted chars
                .replaceAll("\\.", ".~")//replace punctuation with <punct>~
                .replaceAll("\\?", "?~")
                .replaceAll("\\!", "!~");

        return newText;
    }

    public int getDayOfWeek() {
        if (getDate() != null) {
            Calendar c = Calendar.getInstance();
            c.setTime(getDate());
            return c.get(Calendar.DAY_OF_WEEK);
        }

        return -1;
    }

    public ArrayList<String> extractAttachments() throws MessagingException, IOException {
        ArrayList<String> attachments = new ArrayList<>();
        if(message.isMimeType("multipart/*")){
            MimeMultipart mp = (MimeMultipart) message.getContent();
            int count = mp.getCount();
            for(int i = 0; i < count; i ++){
                String fileName = mp.getBodyPart(i).getFileName();
                try {
                    if (fileName != null) attachments.add(fileName.substring(fileName.lastIndexOf(".")).trim().toLowerCase());
                }
                catch(StringIndexOutOfBoundsException e){
                    System.out.println(e.getMessage());
                    System.out.println("Error while extracting file type from attachment " + fileName);
                }
            }
        }
        return attachments;
    }


    private String detectLanguage(String text) {
        LanguageDetector ld = new OptimaizeLangDetector().loadModels();
        ld.addText(text);
        String lang = ld.detect().getLanguage();
        if(lang.equals(""))
            return null;
        return lang;
    }

    public boolean isAnswered(){
        return flags.contains(Flags.Flag.ANSWERED);

    }

    public void addEmailToSender(){ getSender().addEmail(this);}

    public ArrayList<String> getSentences() {
        return sentences;
    }

    public int[] getSentimentScores() {
        return sentimentScores;
    }

    public int getSentencesAnalyzed() {
        return sentencesAnalyzed;
    }

    public double getSentimentPct() {
        return sentimentPct;
    }

    public String getContent() {
        return content;
    }

    public String getTitle() {
        return title;
    }

    public String getSentimentPctStr() {
        return sentimentPctStr;
    }

    public Date getDate() {
        return date;
    }

    public Sender getSender() {
        return sender;
    }

    public Flags getFlags() {
        return flags;
    }

    public String getFolder() {
        return folder;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public ArrayList<String> getAttachments() {
        return attachments;
    }

    public String getLanguage() {
        return language;
    }

    public void setRecipients(Address[] r){
        for(Address a : r){
            recipients.add(a.toString());
        }
    }

    public void setSentimentScores(int[] sentimentScores) {
        this.sentimentScores = sentimentScores;
    }

    public ArrayList<String> getRecipients() {
        return recipients;
    }

    public ArrayList<String> getDomain(boolean sent){
        ArrayList<String> addressesPreFilter = new ArrayList<>();
        ArrayList<String> addressesPostFilter = new ArrayList<>();
        if (sent) {
            for(String r : getRecipients())
                addressesPreFilter.add(r.substring(r.indexOf("@")));
        } else {
            addressesPreFilter.add(getSender().getAddress().substring(getSender().getAddress().indexOf("@")));
        }

        for (String a : addressesPreFilter) {

            int quoteLocation = a.indexOf("\"" /*,address.indexOf("\"")+1*/);
            int caratLocation = a.indexOf(">");
            String d;

            int earlierLocation = -1;

            if (quoteLocation < caratLocation && quoteLocation != -1) {
                earlierLocation = quoteLocation;
            } else {
                if (caratLocation != -1) {
                    earlierLocation = caratLocation;
                }
            }

            if (earlierLocation == -1) {
                //none of the weird characters are found
                addressesPostFilter.add(a);
            } else {
                //some weird characters are found
                addressesPostFilter.add(a.substring(a.indexOf("@"), earlierLocation));
            }
        }

        return addressesPostFilter;
    }

    public String toString() {
        if (this.sentimentPctStr != null)
            return "From: " + this.sender + "\nTitle:" + this.title + "\nDate: " + date + "\nFlags: " + flags.toString()
                    + "\nSentiment: " + this.sentimentPctStr + "\n" + content;
        else
            return "From: " + this.sender + "\nTitle:" + this.title + "\nDate: " + date + "\nFlags: " + flags.toString()
                    + "\n" + content;
    }

}

