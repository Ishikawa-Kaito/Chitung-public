package chitung.plugin.core.responder.universalrespond;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import chitung.plugin.utils.IdentityUtil;
import chitung.plugin.utils.fileutils.Read;
import chitung.plugin.utils.fileutils.Touch;
import chitung.plugin.utils.fileutils.Write;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.MessageEvent;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class URManager {
    static final String UR_PATH = System.getProperty("user.dir") + File.separator + "data" + File.separator + "Chitung" + File.separator + "UniversalResponder.json";

    URManager(){}

    private static final URManager INSTANCE;

    static class URList{
        List<UniversalResponder> universalRespondList;
        URList(){
            this.universalRespondList = new ArrayList<UniversalResponder>(){{add(new UniversalResponder());}};
        }
    }

    static {
        INSTANCE = new URManager();
        initialize();
    }

    public URList urList;

    public static URManager getINSTANCE() {
        return INSTANCE;
    }

    public static void initialize(){
        getINSTANCE().urList = new URList();
        if(Touch.file(UR_PATH)){
            try {
                getINSTANCE().urList = new Gson().fromJson(Read.fromReader(new BufferedReader(new InputStreamReader(new FileInputStream(UR_PATH)))), URList.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            writeRecord();
        }
    }

    public static URList readRecord(){
        URList urList = new URList();
        try {
            urList = new Gson().fromJson(Read.fromReader(new BufferedReader(new InputStreamReader(new FileInputStream(UR_PATH)))), URList.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        URList copiedURList = urList;
        for(UniversalResponder ur:copiedURList.universalRespondList){
            if(ur.getMessageKind()==null||ur.getListKind()==null||ur.getListResponceKind()==null){
                urList.universalRespondList.remove(ur);
                urList.universalRespondList.add(new UniversalResponder(ur));
            }
            if(ur.getPattern().isEmpty()||ur.getAnswer().isEmpty()){
                urList.universalRespondList.remove(ur);
            }
        }
        return urList;
    }

    public static void writeRecord(){
        String jsonString = new GsonBuilder().setPrettyPrinting().create().toJson(getINSTANCE().urList);
        Write.cover(jsonString, UR_PATH);
    }

    static int encodeMessageStatus(MessageEvent event, UniversalResponder ur){
        int code = 0;
        //第一位，Group是2 Friend是1
        if(event instanceof GroupMessageEvent){
            code = 2000;
        } else {
            code = 1000;
        }
        //第二三位，Any 0/Friend 1/Group 2
        switch(ur.getMessageKind()){
            case Any:
                code+=0;
                break;
            case Friend:
                code+=100;
                break;
            case Group:
                code+=200;
                break;
        }

        switch(ur.getListResponceKind()){
            case Any:
                code+=0;
                break;
            case Friend:
                code+=10;
                break;
            case Group:
                code+=20;
                break;
        }
        //第四位黑白名单属性，黑0白1
        switch(ur.getListKind()){
            case Black:
                code+=0;
                break;
            case White:
                code+=1;
                break;
        }
        return code;
    }

    enum MessageResponseKind{
        CheckGroupWhiteList,
        CheckGroupBlackList,
        CheckFriendWhiteList,
        CheckFriendBlackList,
        CheckMemberBlackList,
        CheckMemberWhiteList,
        DoNotRespond,
        Respond
    }

    static MessageResponseKind decodeMessageStatus(int code){
        switch(code){
            case 1020:
            case 1001:
            case 1120:
            case 1101:
            case 2010:
            case 2001:
            case 2201:
                return MessageResponseKind.Respond;
            case 1010:
            case 1110:
                return MessageResponseKind.CheckFriendBlackList;
            case 1011:
            case 1111:
                return MessageResponseKind.CheckFriendWhiteList;
            case 2020:
            case 2220:
                return MessageResponseKind.CheckGroupBlackList;
            case 2021:
            case 2221:
                return MessageResponseKind.CheckGroupWhiteList;
            case 2210:
                return MessageResponseKind.CheckMemberBlackList;
            case 2211:
                return MessageResponseKind.CheckMemberWhiteList;
            default:
                return MessageResponseKind.DoNotRespond;
        }
    }

    static boolean IDMatch(MessageEvent event, UniversalResponder ur){
        switch(decodeMessageStatus(encodeMessageStatus(event,ur))){
            case DoNotRespond:
                return false;
            case Respond:
                return true;
            case CheckFriendBlackList:
            case CheckMemberBlackList:
                return !ur.getUserList().contains(event.getSender().getId());
            case CheckFriendWhiteList:
            case CheckMemberWhiteList:
                return ur.getUserList().contains(event.getSender().getId());
            case CheckGroupBlackList:
                return !ur.getUserList().contains(event.getSubject().getId());
            case CheckGroupWhiteList:
                return ur.getUserList().contains(event.getSubject().getId());
        }
        return false;
    }

    static boolean contentMatch(String string, UniversalResponder ur){
        switch(ur.getTriggerKind()){
            case Equal:
                for(String pattern:ur.getPattern()){
                    if(string.equals(pattern)) return true;
                }
                break;
            case Contain:
                for(String pattern:ur.getPattern()){
                    if(string.contains(pattern)) return true;
                }
                break;
        }
        return false;
    }

    static boolean kindMatch(MessageEvent event, UniversalResponder ur){
        if(ur.getMessageKind().equals(MessageKind.Any)) return true;
        if(ur.getListResponceKind().equals(MessageKind.Any)) return true;
        return (ur.getMessageKind().equals(ur.getListResponceKind()));
    }

    static void respond(MessageEvent event){
        for(UniversalResponder ur:getINSTANCE().urList.universalRespondList) {
            if(!kindMatch(event,ur)) continue;
            if(!contentMatch(event.getMessage().contentToString(),ur)) continue;
            if(!IDMatch(event,ur)) continue;
            Random random = new Random();
            int n = random.nextInt(ur.getAnswer().size());
            event.getSubject().sendMessage(ur.getAnswer().get(n));
            break;
        }
    }

    static void reset(MessageEvent event){
        if(!IdentityUtil.isAdmin(event)) return;
        if(event.getMessage().contentToString().toLowerCase().contains("/reset")&&(event.getMessage().contentToString().toLowerCase().contains("ur"))){
            getINSTANCE().urList=readRecord();
            event.getSubject().sendMessage("已经重置 Universal Responder 的配置文件。");
        }
    }

    public static void handle(MessageEvent event){
        reset(event);
        respond(event);
    }

    public void ini(){}
}