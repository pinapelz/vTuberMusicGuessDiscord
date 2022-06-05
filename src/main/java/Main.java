import audio.Music;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.security.auth.login.LoginException;
import java.io.*;


public class Main extends ListenerAdapter {
    public static JDABuilder jdabuilder = JDABuilder.createDefault(readSetting("discordToken")).addEventListeners(new Main());
    public static JDA jda;
    public static void main(String args[]) {
        try {
            jdabuilder.addEventListeners(new Music(jda,readSetting("youtubeApi")));
            jda = jdabuilder.build();
            System.out.println("Bot Started");
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }

    }

    public static String readSetting(String parameter) {
        Object obj = null;
        try {
            obj = new JSONParser().parse(new FileReader("settings//config.json"));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        JSONObject jo = (JSONObject) obj;
        return (String) jo.get(parameter);

    }
    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        JDA jda = e.getJDA();
        Message message = e.getMessage();
        String msg = message.getContentDisplay();
    }
    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Loading Complete");
    }
}
