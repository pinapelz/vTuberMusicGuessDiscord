package audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


public class Music extends ListenerAdapter {
    private HashMap<String, String> memberIDMap = fillHashMapFromSite("guessingSongs.txt");//guessingSongs.txt
    private Set<String> keySet = memberIDMap.keySet();
    private HashMap<String, Integer> leaderboard = new HashMap();
    private Collection<String> values = memberIDMap.values();
    private ArrayList<String> listOfKeys = new ArrayList<String>(values); //TITLES
    private ArrayList<String> listOfValues = new ArrayList<String>(keySet); //URL
    //Variables initialized
    private String nameWinner = "";
    private String titleWinner = "";
    private String currUrl = "";
    private String apiKey = "";
    private boolean forceNext = false;
    private long destinationTimestamp = 0;
    private double difficultyAuthor = 0.8;
    private double difficultyTitle = 0.7;
    private int roundNumber = 0;
    private boolean hardMode = true;
    private boolean revealAnswer = false;
    private static String append = "$";
    private boolean listeningPeriod = false;
    private boolean randomizedTime = false;
    private boolean guessingPeriod = false;
    private int startPosition = 0;
    private boolean threadStarted = false;
    private boolean nameGuessed = false;
    private boolean titleGuessed = false;
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;
    private String answer = "";
    JDA jda;

    public Music(JDA jda, String apiKey) {//JDA initializing
        this.musicManagers = new HashMap<>();
        this.jda = jda;
        this.apiKey = apiKey;
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }


    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        GuildMusicManager mng = getGuildAudioPlayer(guild);
        AudioPlayer player = mng.player;
        TrackScheduler scheduler = mng.scheduler;
        String rawMessage = message.getContentDisplay();
        String[] command = event.getMessage().getContentRaw().split(" ", 2);
        if (!guessingPeriod && !event.getAuthor().isBot()) { //Checking for while the game is not currently running and user is not bot
            if ((append + "size").equals(command[0]) && command.length == 1) { //Pull the size of the song list
                event.getChannel().sendMessage("Currently there are " + memberIDMap.size() + " songs in the list").queue();
            }
            if ((append + "hardmode").equals(command[0]) && command.length == 1) { //Setting hardmode and easymode
                hardMode = !hardMode;
                event.getChannel().sendMessage("Boolean hardMode is now set to " + hardMode).queue();
            }

            if ((append + "leaderboard").equals(command[0]) && command.length == 1) { //Show leaderboard in chat
                String msg = "```Current Standings\n";
                for (String name : leaderboard.keySet()) {
                    String key = name;
                    String value = leaderboard.get(name).toString();
                    System.out.println(key + " " + value);
                    msg = msg.concat(key + ": " + value + "\n");
                }
                msg = msg.concat("```");
                event.getChannel().sendMessage(msg).queue();
            }
            if ((append + "saveboard").equals(command[0]) && command.length == 1) {
                saveHashmap(leaderboard, "leaderboard.txt");
                event.getChannel().sendMessage("The current leaderboard has been saved").queue();
            }
            if ((append + "loadboard").equals(command[0]) && command.length == 1 && event.getAuthor().getName().equalsIgnoreCase("pinapelz")) { //Stupid debug line. Will change to proper role later
                leaderboard.clear();

                leaderboard = loadHashmap("leaderboard.txt");
                event.getChannel().sendMessage("Leaderboard Loaded").queue();
            }
            if ((append + "nextround").equals(command[0]) || (append + "nr").equals(command[0]) && command.length == 1) {

                Runnable forceRun = () -> { //Initiate thread that checks for if the song plays for too long
                    try {
                        TimeUnit.SECONDS.sleep(30); //Max 30 seconds of buffering and playing. Otherwise we gotta move on
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (player.getPlayingTrack() == null) {
                        return;
                    } else {
                    }
                    scheduler.queue.clear();
                    player.stopTrack();
                    player.setPaused(false);
                    event.getChannel().sendMessage("The 20 Seconds Guessing Period will Begin from the First Guess").queue();

                    guessingPeriod = true;
                    listeningPeriod = false;
                    System.out.println("Timed out. Possibly Bugged or Buffering too much Skipping to Answer Phase");

                };
                event.getChannel().sendMessage("Take a listen to this clip! Audio bugs present in hard mode.").queue();
                nameWinner = "";
                titleWinner = "";
                scheduler.queue.clear(); //Ensure queue is empty
                player.stopTrack();
                nameGuessed = false;
                destinationTimestamp = 0L;
                startPosition = 0;
                forceNext = false;
                Thread th = new Thread(forceRun); //Thread to check for timeout
                randomizedTime = false;
                titleGuessed = false;
                Random r = new Random();
                int randomIndex = r.nextInt(listOfValues.size());
                System.out.println("Game on!");
                listeningPeriod = true;
                answer = listOfKeys.get(randomIndex);
                System.out.println("Playing Song");
                currUrl = listOfValues.get(randomIndex);
                if (currUrl.equals("")) { //If the code pulls a blank line from the website then go one less the index to avoid the blank line
                    System.out.println("blank going --");
                    randomIndex--;
                    currUrl = listOfValues.get(randomIndex);
                }
                loadAndPlay(event.getChannel(), listOfValues.get(randomIndex), false);
                Runnable runnable = () -> { //Thread used to track and calculate where the position should be set to and where it should end
                    if (hardMode) {
                        Random rand = new Random();
                        rand.setSeed(System.currentTimeMillis());
                        int currentPositionInt = 0;
                        int destinationInt = 0;
                        while (listeningPeriod) {
                            try {
                                AudioTrack currentTrack = player.getPlayingTrack();
                                if (!randomizedTime) {
                                    currentTrack.setPosition(0L);
                                    long maximumDuration = currentTrack.getDuration();
                                    int endPoint = r.nextInt(((int) maximumDuration) - 5500) + 15000;
                                    startPosition = (endPoint - 15000);
                                    if (startPosition > 300000 || startPosition <= 0 || endPoint > maximumDuration) { //In case of any weird variable bugs reset everything to 0
                                        startPosition = 0;
                                        endPoint = 15000;
                                        System.out.println("Error. Resetting to 0");
                                    }
                                    while (startPosition != currentTrack.getPosition()) {//Keep spamming the bot to set the position to startPosition
                                        currentTrack.setPosition(Integer.toUnsignedLong(startPosition));
                                        th.start(); //th Thread is tracking for 30 second timeout
                                    }
                                    System.out.println("Tracking from " + startPosition + " to " + endPoint);
                                    destinationInt = endPoint;
                                    randomizedTime = true;
                                }

                                currentPositionInt = Math.toIntExact(currentTrack.getPosition());

                                if (currentPositionInt >= destinationInt || forceNext) {//Check for when the audio has played in full
                                    if (player.getPlayingTrack() == null) {
                                        return;
                                    }
                                    else {
                                    }
                                    scheduler.queue.clear();
                                    player.stopTrack();
                                    player.setPaused(false);
                                    //  event.getChannel().sendMessage("Playback has been completely stopped and the queue has been cleared.").queue();
                                    event.getChannel().sendMessage("The 20 Seconds Guessing Period will Begin from the First Guess").queue();
                                    System.out.println(answer);
                                    guessingPeriod = true;
                                    listeningPeriod = false;

                                }
                            } catch (Exception e) {
                            }
                        }


                    } else if (!hardMode) {
                        while (listeningPeriod) {
                            try {
                                AudioTrack currentTrack = player.getPlayingTrack();
                                if (getTimestamp(currentTrack.getPosition()).equals("00:15")) {
                                    if (player.getPlayingTrack() == null) {
                                        return;
                                    }
                                    player.setPaused(!player.isPaused());
                                    if (player.isPaused()) {
                                    } else {
                                    }
                                    scheduler.queue.clear();
                                    player.stopTrack();
                                    player.setPaused(false);
                                    //  event.getChannel().sendMessage("Playback has been completely stopped and the queue has been cleared.").queue();
                                    event.getChannel().sendMessage("The 20 Seconds Guessing Period will Begin from the First Guess").queue();
                                    guessingPeriod = true;
                                    listeningPeriod = false;

                                }
                            } catch (Exception e) {
                            }
                        }

                    }

                };


                Thread thread = new Thread(runnable);
                thread.start();

            }
        } else if (guessingPeriod && !event.getAuthor().isBot()) {
            String[] splitString = answer.split("<");
            String nameAnswer = splitString[1];
            String titleAnswer = splitString[0];
            Runnable runnable = () -> {
                threadStarted = true;
                if (guessingPeriod) {
                    int fiveSecondsElapsed = 0;
                    //Internal clock. Timeout after 20 secs  of wrong guesses
                    for (int i=0;i<20;i++){
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if(i%5==0){
                            fiveSecondsElapsed++;
                            event.getChannel().sendMessage(25-(fiveSecondsElapsed*5)+ " Seconds Remaining").queue();
                        }

                    }
                    System.out.println("Times up!");
                    if (revealAnswer) {
                        revealAnswer = false;
                    } else if (!revealAnswer) {
                        revealAnswer = true;
                        sendAnswer(event);

                    }
                    event.getChannel().sendMessage("Times up! Ready for the next round!").queue();
                    guessingPeriod = false;
                    threadStarted = false;
                } else {
                    System.out.println("Guessing End");

                }

            };
            Thread thread = new Thread(runnable);
            if (!threadStarted) {
                thread.start();
            } else {

            }

            if (rawMessage.contains("&")) {//For guessing names with the & symbol. This allows the names to be written in any order
                System.out.println("MESSAGE HAS APPEND SYMBOL");
                boolean missingName = false;//Assume that the answer is correct
                String[] names = nameAnswer.split("&"); //Spltting name answer pulled from site

                for (int i = 0; i < names.length; i++) {
                    System.out.println("Checking " + rawMessage.toLowerCase() + " for contains " + names[i]);
                    if (rawMessage.toLowerCase().contains(names[i].toLowerCase())) {
                        //If the message does contain the name being checked then do nothing
                    } else {
                        missingName = true; //If not alert that there is a name missing
                    }
                }
                if (!missingName) {
                    event.getChannel().sendMessage(event.getAuthor().getAsMention() + " guessed the artist!").queue();
                    nameGuessed = true;
                    nameWinner = event.getAuthor().getName();
                    if (nameGuessed && titleGuessed) {
                        event.getChannel().sendMessage("All points have been obtained! Please wait as we finish the round!").queue();
                    }
                } else {
                    //do nothing if they did not guess the artist name correctly
                }
                if (findSimilarity((rawMessage.toLowerCase()), nameAnswer.toLowerCase()) >= difficultyAuthor && !nameGuessed) { //Levenshtien distance
                    event.getChannel().sendMessage(event.getAuthor().getAsMention() + " guessed the artist!").queue();
                    nameGuessed = true;
                    nameWinner = event.getAuthor().getName();
                    if (nameGuessed && titleGuessed) {
                        event.getChannel().sendMessage("All points have been obtained! Please wait as we finish the round!").queue();
                    }
                }
            } else if (!rawMessage.contains("+")) {
                if (findSimilarity((rawMessage.toLowerCase()), nameAnswer.toLowerCase()) >= difficultyAuthor && !nameGuessed) {
                    event.getChannel().sendMessage(event.getAuthor().getAsMention() + " guessed the artist!").queue();
                    nameGuessed = true;
                    nameWinner = event.getAuthor().getName();
                    if (nameGuessed && titleGuessed) {
                        event.getChannel().sendMessage("All points have been obtained! Please wait as we finish the round!").queue();
                    }
                }
            }
            if (findSimilarity(rawMessage.toLowerCase(), titleAnswer.toLowerCase()) >= difficultyTitle && !titleGuessed) {
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " guessed the title!").queue();
                titleGuessed = true;
                titleWinner = event.getAuthor().getName();
                if (nameGuessed && titleGuessed) {
                    event.getChannel().sendMessage("All points have been obtained! Please wait as we finish the round!").queue();
                }
            }


        }

        super.onGuildMessageReceived(event);
    }

    public static int getLevenshteinDistance(String X, String Y) {//Levenshtein distance function to check answer similarity
        int m = X.length();
        int n = Y.length();
        int[][] T = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            T[i][0] = i;
        }
        for (int j = 1; j <= n; j++) {
            T[0][j] = j;
        }
        int cost;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                cost = X.charAt(i - 1) == Y.charAt(j - 1) ? 0 : 1;
                T[i][j] = Integer.min(Integer.min(T[i - 1][j] + 1, T[i][j - 1] + 1),
                        T[i - 1][j - 1] + cost);
            }
        }

        return T[m][n];
    }

    public static double findSimilarity(String x, String y) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }

        double maxLength = Double.max(x.length(), y.length());
        if (maxLength > 0) {
            // optionally ignore case if needed
            return (maxLength - getLevenshteinDistance(x, y)) / maxLength;
        }
        return 1.0;
    }


    public static HashMap<String, String> fillHashMapFromSite(String fileName) { //Pulling song list from my site
        try {
            URL url = new URL("https://pinapelz.github.io/vTuberDiscordBot/" + fileName);
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;
            FileWriter writer = new FileWriter("data//" + fileName);
            while ((line = in.readLine()) != null) {
                writer.write(line + "\n");
            }
            writer.close();
            in.close();
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O Error: " + e.getMessage());
        }
        String delimiter = ">";
        HashMap<String, String> map = new HashMap<>();
        try (Stream<String> lines = Files.lines(Paths.get("data//" + fileName))) { //Pulled as hashmap and stored to a local textfile
            lines.filter(line -> line.contains(delimiter)).forEach(line ->
                    map.putIfAbsent(line.split(delimiter)[0]
                            , line.split(delimiter)[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl, boolean returnMessage) { //Load and play music
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (returnMessage) {
                    channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

                }

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }
                if (returnMessage) {
                    channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();
                }


                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (returnMessage) {
                    channel.sendMessage("Could not play: " + exception.getMessage()).queue();
                }
            }
        });
    }


    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        //Reference MP3 Here in the Future?
        connectToFirstVoiceChannel(guild.getAudioManager());
        musicManager.scheduler.queue(track);
        BlockingQueue<AudioTrack> s = musicManager.scheduler.queue;

    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) { //Depecrated but im lazy
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private static String getTimestamp(long milliseconds) { //Calculate timestamp from ms
        int seconds = (int) (milliseconds / 1000) % 60;
        int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
        int hours = (int) ((milliseconds / (1000 * 60 * 60)) % 24);

        if (hours > 0)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else
            return String.format("%02d:%02d", minutes, seconds);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }


    public void sendAnswer(GuildMessageReceivedEvent event) { //Send the answer to chat in the form of an embed
        if (revealAnswer) {
            roundNumber++;
            System.out.println("");
            answer = answer.replaceAll("<", " by ");
            try {
                String[] splitter = currUrl.split("v=");
                String vidID = splitter[1];
                EmbedBuilder embed = new EmbedBuilder()
                        .setDescription(answer + "\n" + currUrl)
                        .setTitle("The answer was... ")
                        .setImage("https://img.youtube.com/vi/" + vidID + "/hqdefault.jpg");
                MessageBuilder messageBuilder = (MessageBuilder) new MessageBuilder().setEmbeds(embed.build());
                event.getChannel().sendMessage(messageBuilder.build()).queue();


            } catch (Exception ef) {
            }
            event.getChannel().sendMessage("**Round " + roundNumber + " fastest answers:**\n *Song Name Winner:* " + titleWinner + "\n*Artist Winner:* " + nameWinner).queue();
            if (!titleWinner.equals("")) { //awarding points by adding to the leaderboard hashmap
                if (leaderboard.containsKey(titleWinner)) {
                    leaderboard.put(titleWinner, leaderboard.get(titleWinner) + 2);
                } else {
                    leaderboard.put(titleWinner, 2);

                }
            }
            if (!nameWinner.equals("")) {
                if (leaderboard.containsKey(nameWinner)) {
                    leaderboard.put(nameWinner, leaderboard.get(nameWinner) + 1);
                } else {
                    leaderboard.put(nameWinner, 1);
                }


            }
        }
        String msg = "```Current Standings\n";
        for (String name : leaderboard.keySet()) {
            String key = name;
            String value = leaderboard.get(name).toString();
            System.out.println(key + " " + value);
            msg = msg.concat(key + ": " + value + "\n");
        }
        msg = msg.concat("```");
        event.getChannel().sendMessage(msg).queue();
        revealAnswer = false;
    }

    public void saveHashmap(HashMap<String, Integer> ldapContent, String pathString) {
        Path path = Paths.get(pathString);
        try {
            Files.write(path, () -> ldapContent.entrySet().stream()
                    .<CharSequence>map(e -> e.getKey() + ":" + e.getValue())
                    .iterator());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HashMap<String, Integer> loadHashmap(String path) {
        String delimiter = ":";
        HashMap<String, Integer> map = new HashMap<>();
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
            lines.filter(line -> line.contains(delimiter)).forEach(line ->
                    map.putIfAbsent(line.split(delimiter)[0]
                            , Integer.valueOf(line.split(delimiter)[1])));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }
}
