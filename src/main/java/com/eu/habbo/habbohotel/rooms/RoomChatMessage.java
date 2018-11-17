package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Loggable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class RoomChatMessage implements Runnable, ISerialize, Loggable
{
    public static String insertQuery = "INSERT INTO chatlogs_room (user_from_id, user_to_id, message, timestamp, room_id) VALUES (?, ?, ?, ?, ?)";
    public static int MAXIMUM_LENGTH = 100;
    //Configuration. Loaded from database & updated accordingly.
    public static boolean SAVE_ROOM_CHATS = false;
    public static int[] BANNED_BUBBLES = {};

    private int roomUnitId;
    private String message;
    private String unfilteredMessage;
    private int timestamp = 0;
    private RoomChatMessageBubbles bubble;
    private final Habbo habbo;
    public int roomId;
    private Habbo targetHabbo;
    private byte emotion;
    public boolean isCommand = false;
    public boolean filtered = false;
    private static final List<String> chatColors = Arrays.asList("@red@", "@cyan@", "@blue@", "@green@", "@purple@");

    public RoomChatMessage(MessageHandler message)
    {
        if(message.packet.getMessageId() == Incoming.RoomUserWhisperEvent)
        {
            String data = message.packet.readString();
            this.targetHabbo = message.client.getHabbo().getHabboInfo().getCurrentRoom().getHabbo(data.split(" ")[0]);
            this.message = data.substring(data.split(" ")[0].length() + 1, data.length());
        }
        else
        {
            this.message = message.packet.readString();
        }
        try
        {
            this.bubble = RoomChatMessageBubbles.getBubble(message.packet.readInt());
        }
        catch (Exception e)
        {
            this.bubble = RoomChatMessageBubbles.NORMAL;
        }
        
        if(message.client != null && message.client.getHabbo() != null && !message.client.getHabbo().hasPermission("acc_anychatcolor"))
        {
            for(Integer i : RoomChatMessage.BANNED_BUBBLES)
            {
                if(i == this.bubble.getType())
                {
                    this.bubble = RoomChatMessageBubbles.NORMAL;
                }
            }
        }

        this.unfilteredMessage = this.message;
        this.habbo = message.client.getHabbo();
        this.roomUnitId = habbo.getRoomUnit().getId();
        this.timestamp = Emulator.getIntUnixTimestamp();

        this.checkEmotion();

        this.filter();
    }

    public RoomChatMessage(RoomChatMessage chatMessage)
    {
        this.message = chatMessage.getMessage();
        this.unfilteredMessage = chatMessage.getUnfilteredMessage();
        this.habbo = chatMessage.getHabbo();
        this.targetHabbo = chatMessage.getTargetHabbo();
        this.bubble = chatMessage.getBubble();
        this.roomUnitId = chatMessage.roomUnitId;
        this.emotion = (byte)chatMessage.getEmotion();
    }

    public RoomChatMessage(String message, RoomUnit roomUnit, RoomChatMessageBubbles bubble)
    {
        this.message = message;
        this.unfilteredMessage = message;
        this.habbo = null;
        this.bubble = bubble;
        this.roomUnitId = roomUnit.getId();
    }

    public RoomChatMessage(String message, Habbo habbo, RoomChatMessageBubbles bubble)
    {
        this.message = message;
        this.unfilteredMessage = message;
        this.habbo = habbo;
        this.bubble = bubble;
        this.checkEmotion();
        this.roomUnitId = habbo.getRoomUnit().getId();
        this.message = this.message.replace("\r", "").replace("\n", "");

        if(this.bubble.isOverridable() && this.getHabbo().getHabboStats().chatColor != RoomChatMessageBubbles.NORMAL)
            this.bubble = this.getHabbo().getHabboStats().chatColor;
    }

    public RoomChatMessage(String message, Habbo habbo, Habbo targetHabbo, RoomChatMessageBubbles bubble)
    {
        this.message = message;
        this.unfilteredMessage = message;
        this.habbo = habbo;
        this.targetHabbo = targetHabbo;
        this.bubble = bubble;
        this.checkEmotion();
        this.roomUnitId = this.habbo.getRoomUnit().getId();
        this.message = this.message.replace("\r", "").replace("\n", "");

        if(this.bubble.isOverridable() && this.getHabbo().getHabboStats().chatColor != RoomChatMessageBubbles.NORMAL)
            this.bubble = this.getHabbo().getHabboStats().chatColor;
    }

    private void checkEmotion()
    {
        if(this.message.contains(":)") || this.message.contains(":-)") || this.message.contains(":]"))
        {
            this.emotion = 1;
        }
        else if(this.message.contains(":@") || this.message.contains(">:("))
        {
            this.emotion = 2;
        }
        else if(this.message.contains(":o") || this.message.contains(":O") || this.message.contains(":0") || this.message.contains("O.o") || this.message.contains("o.O") || this.message.contains("O.O"))
        {
            this.emotion = 3;
        }
        else if(this.message.contains(":(") || this.message.contains(":-(") || this.message.contains(":["))
        {
            this.emotion = 4;
        }
    }

    @Override
    public synchronized void run()
    {
        if(habbo == null)
            return;

        if(this.message.length() > RoomChatMessage.MAXIMUM_LENGTH)
        {
            try
            {
                this.message = this.message.substring(0, RoomChatMessage.MAXIMUM_LENGTH-1);
            }
            catch (Exception e)
            {
                Emulator.getLogging().logErrorLine(e);
            }
        }
        Emulator.getLogging().addChatLog(this);
    }

    public String getMessage()
    {
        return this.message;
    }

    public String getUnfilteredMessage()
    {
        return this.unfilteredMessage;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public RoomChatMessageBubbles getBubble()
    {
        return this.bubble;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public Habbo getTargetHabbo()
    {
        return this.targetHabbo;
    }

    public int getEmotion()
    {
        return this.emotion;
    }

    @Override
    public void serialize(ServerMessage message)
    {
        if(this.habbo != null && this.bubble.isOverridable())
        {
            if (!this.habbo.hasPermission("acc_anychatcolor"))
            {
                for (Integer i : RoomChatMessage.BANNED_BUBBLES)
                {
                    if (i == this.bubble.getType())
                    {
                        this.bubble = RoomChatMessageBubbles.NORMAL;
                        break;
                    }
                }
            }
        }

        if (!this.getBubble().getPermission().isEmpty())
        {
            if (this.habbo != null && !this.habbo.hasPermission(this.getBubble().getPermission()))
            {
                this.bubble = RoomChatMessageBubbles.NORMAL;
            }
        }

        try
        {
            message.appendInt(this.roomUnitId);
            message.appendString(this.getMessage());
            message.appendInt(this.getEmotion());
            message.appendInt(this.getBubble().getType());
            message.appendInt(0);
            message.appendInt(this.getMessage().length());
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void filter()
    {
        if(!habbo.getHabboStats().hasActiveClub())
        {
            for (String chatColor : chatColors) {
                message = message.replace(chatColor, "");
            }
        }
        
        if(Emulator.getConfig().getBoolean("hotel.wordfilter.enabled") && Emulator.getConfig().getBoolean("hotel.wordfilter.rooms"))
        {
            if(!habbo.hasPermission("acc_chat_no_filter"))
            {
                if (!Emulator.getGameEnvironment().getWordFilter().autoReportCheck(this))
                {
                    if (!Emulator.getGameEnvironment().getWordFilter().hideMessageCheck(this.message))
                    {
                        Emulator.getGameEnvironment().getWordFilter().filter(this, this.habbo);
                        return;
                    }
                }
                else
                {
                    habbo.mute(Emulator.getConfig().getInt("hotel.wordfilter.automute"));
                }

                this.message = "";
            }
        }
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException
    {
        statement.setInt(1,this.habbo.getHabboInfo().getId());

        if(this.targetHabbo != null)
            statement.setInt(2, this.targetHabbo.getHabboInfo().getId());
        else
            statement.setInt(2, 0);

        statement.setString(3, this.unfilteredMessage);
        statement.setInt(4, this.timestamp);

        if(this.habbo.getHabboInfo().getCurrentRoom() != null)
        {
            statement.setInt(5, this.habbo.getHabboInfo().getCurrentRoom().getId());
        }
        else
        {
            statement.setInt(5, 0);
        }

        statement.addBatch();
    }
}
