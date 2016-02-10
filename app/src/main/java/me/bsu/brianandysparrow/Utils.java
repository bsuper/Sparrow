package me.bsu.brianandysparrow;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import com.activeandroid.query.Select;
import com.activeandroid.util.SQLiteUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.bsu.brianandysparrow.models.DBTweet;
import me.bsu.brianandysparrow.models.DBVectorClockItem;
import me.bsu.proto.Tweet;
import me.bsu.proto.TweetExchange;
import me.bsu.proto.VectorClockItem;

public class Utils {

    // Settings keys
    public static final String MY_UUID_KEY = "USER_UUID_KEY";
    public static final String MY_VC_TIME_KEY = "VC_TIME_KEY";
    // Shared preferences file
    public static final String PREFS_NAME = "MyPrefsFile";

    public static String MY_UUID = null;

    /**
     * Returns a display string from a bluetooth device.
     *
     * @param device
     * @return
     */
    public static String deviceToString(BluetoothDevice device) {
        return device.getName() + "\n" + device.getAddress();
    }

    /**
     * Returns a list of strings representing each device from a set of Bluetooth devices
     *
     * @param bluDevices
     * @return
     */
    public static String convertDevicesToString(List<BluetoothDevice> bluDevices) {
        String devices = "";
        for (BluetoothDevice device : bluDevices) {
            devices += Utils.deviceToString(device);
        }
        return devices;
    }

    // SEND MESSAGES UTILS

    public static void constructNewTweet(Context context, String msg) {
        int tweetID = 12412;
        String author = "Brian";
        String recipient = "";
        String senderUUID = getOrCreateNewUUID(context).toString();

        // Construct database entry
        DBTweet dbTweet = new DBTweet(tweetID, author, msg, recipient, senderUUID);
        dbTweet.save();

        // Get Vector Clocks
        List<VectorClockItem> vectorClockItems = createVectorClockArrayForNewMessage(context);
        // Add my new vector clock
        vectorClockItems.add(new VectorClockItem.Builder().clock(incrementVCTime(context)).uuid(senderUUID).build());


        // Create a DBVectorClockItem item for each vector clock item and save it to db
        for (VectorClockItem vc : vectorClockItems) {
            new DBVectorClockItem(vc.uuid, vc.clock, dbTweet).save();
        }
    }

    public static TweetExchange constructTweetExchangeWithLatestNTweets(int n) {
        List<DBTweet> dbTweets = new Select().from(DBTweet.class).execute();
        Collections.sort(dbTweets);

        List <Tweet> tweets = new ArrayList<>();
        for (DBTweet dbTweet : dbTweets) {
            Tweet tweet = new Tweet.Builder()
                    .id(dbTweet.tweetID)
                    .author(dbTweet.author)
                    .content(dbTweet.content)
                    .recipient(dbTweet.recipient)
                    .sender_uuid(dbTweet.senderUUID)
                    .vector_clocks(dbTweet.vectorClockItems())
                    .build();
            tweets.add(tweet);
        }
        return new TweetExchange.Builder().tweets(tweets).build();
    }


    public static List<VectorClockItem> createVectorClockArrayForNewMessage(Context context) {
        String myUUID = getOrCreateNewUUID(context).toString();

        List<VectorClockItem> vectorClockItems = new ArrayList<>();
        List<DBVectorClockItem> dbVectorClockItems = SQLiteUtils.rawQuery(DBVectorClockItem.class,
                "SELECT *, MAX(clock) FROM DBVectorClockItems GROUP BY `UUID`", new String[]{});
        for (DBVectorClockItem dbVC : dbVectorClockItems) {
            if (!dbVC.uuid.equals(myUUID)){
                VectorClockItem vcItem = new VectorClockItem.Builder().clock(dbVC.clock).uuid(dbVC.uuid).build();
                vectorClockItems.add(vcItem);
            }
        }
        return vectorClockItems;
    }

    public static void readTweetExchangeSaveTweetsInDB(TweetExchange te) {
        List<Tweet> tweets = te.tweets;
        for (Tweet t : tweets) {
            DBTweet dbTweet = new DBTweet(t.id, t.author, t.content, t.recipient, t.sender_uuid);
            dbTweet.save();

            for (VectorClockItem vc : t.vector_clocks) {
                new DBVectorClockItem(vc.uuid, vc.clock, dbTweet).save();
            }
        }
    }


    // METHODS FOR SETTINGS AND PREFERENCES

    public static UUID getOrCreateNewUUID(Context context) {
        SharedPreferences settings = context.getSharedPreferences(Utils.PREFS_NAME, 0);

        String uuidString = settings.getString(Utils.MY_UUID_KEY, null);
        if (uuidString == null) {
            return Utils.generateNewUUID(settings);
        } else {
            return UUID.fromString(uuidString);
        }
    }

    /**
     * Generate and store a new UUID for this user
     * @param prefs
     */
    public static UUID generateNewUUID(SharedPreferences prefs) {
        UUID uuid = UUID.randomUUID();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(MY_UUID_KEY, uuid.toString());
        editor.commit();
        return uuid;
    }

    public static int incrementVCTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(Utils.PREFS_NAME, 0);
        int vcTime = settings.getInt(Utils.MY_VC_TIME_KEY, -1);
        // Fetch our current vector clock time
        if (vcTime == -1) {
             vcTime = Utils.generateNewVCTime(settings);
        }
        settings.edit().putInt(MY_VC_TIME_KEY, vcTime + 1);
        return vcTime + 1;
    }

    public static int getOrCreateNewVCTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(Utils.PREFS_NAME, 0);
        int vcTime = settings.getInt(Utils.MY_VC_TIME_KEY, -1);
        // Fetch our current vector clock time
        if (vcTime == -1) {
            return Utils.generateNewVCTime(settings);
        } else {
            return vcTime;
        }
    }

    /**
     * Create a new vector clock time for the user
     */
    public static int generateNewVCTime(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(MY_VC_TIME_KEY, 0);
        editor.commit();
        return 0;
    }
}