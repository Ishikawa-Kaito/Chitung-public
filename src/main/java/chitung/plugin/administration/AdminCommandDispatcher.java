package chitung.plugin.administration;

import chitung.plugin.administration.config.ConfigHandler;
import chitung.plugin.core.responder.Blacklist;
import chitung.plugin.utils.IdentityUtil;
import net.mamoe.mirai.event.events.MessageEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminCommandDispatcher {
    static final AdminCommandDispatcher INSTANCE = new AdminCommandDispatcher();
    final ExecutorService executor;


    public AdminCommandDispatcher() {
        this.executor = Executors.newCachedThreadPool();
    }

    public static AdminCommandDispatcher getInstance() {
        return INSTANCE;
    }


    public void handleMessage(MessageEvent event){
        if(!IdentityUtil.isAdmin(event)) return;
        AdminTools.getINSTANCE().handleAdminCommand(event);
        //黑名单
        Blacklist.BlacklistOperation(event);
        //设置管理
        ConfigHandler.react(event);
    }

    public void close() {
        executor.shutdown();
    }
}