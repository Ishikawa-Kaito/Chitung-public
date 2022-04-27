package mirai.chitung.plugin.core.responder.lotterywinner;

import com.google.common.collect.HashBasedTable;
import mirai.chitung.plugin.administration.config.ConfigHandler;
import mirai.chitung.plugin.core.groupconfig.GroupConfigManager;
import mirai.chitung.plugin.core.responder.RespondTask;
import mirai.chitung.plugin.utils.IdentityUtil;
import mirai.chitung.plugin.utils.StandardTimeUtil;
import mirai.chitung.plugin.utils.image.ImageCreater;
import net.mamoe.mirai.contact.*;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.PlainText;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class LotteryMachine {
    static final Timer TIMER = new Timer(true);
    static final ConcurrentHashMap<Long, Boolean> C4_ACTIVATION_FLAGS = new ConcurrentHashMap<>();
    static final HashBasedTable<Long,Long,Boolean> BUMMER_ACTIVATION_FLAG = HashBasedTable.create();
    static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(10);
    static final Random rand = new Random();

    static {
        //每日6点定时清空C4触发标记
        TIMER.schedule(new TimerTask() {
                           @Override
                           public void run() {
                               LotteryMachine.C4_ACTIVATION_FLAGS.clear();
                           }
                       },
                StandardTimeUtil.getStandardFirstTime(0, 0, 1),
                StandardTimeUtil.getPeriodLengthInMS(1, 0, 0, 0));
    }

    public static boolean botPermissionChecker(GroupMessageEvent event) {
        return ((event.getGroup().getBotPermission().equals(MemberPermission.ADMINISTRATOR)) || (event.getGroup().getBotPermission().equals(MemberPermission.OWNER)));
    }

    public static boolean senderPermissionChecker(GroupMessageEvent event) {
        return ((event.getSender().getPermission().equals(MemberPermission.ADMINISTRATOR)) || (event.getSender().getPermission().equals(MemberPermission.OWNER)));
    }

    public static boolean sessionChecker(GroupMessageEvent event){
        return BUMMER_ACTIVATION_FLAG.rowKeySet().contains(event.getGroup().getId())&&BUMMER_ACTIVATION_FLAG.row(event.getGroup().getId()).containsKey(event.getSender().getId());
    }

    public static RespondTask okBummer(GroupMessageEvent event, RespondTask.Builder builder) {

        if(LotteryBummerExclusion.getINSTANCE().exclusionClass.userList.contains(event.getSender().getId())) {
            builder.addMessage(new At(event.getSender().getId()).plus("您已经开启Bummer保护。"));
            return builder.build();
        }

        if(!GroupConfigManager.lotteryConfig(event)) {
            builder.addMessage("本群暂未开启Bummer功能。");
            builder.addNote("群 " + event.getGroup().getId() + " 尝试发起Bummer功能，但该群未开启该功能。");
            return builder.build();
        }

        if(!ConfigHandler.getINSTANCE().config.getGroupFC().isLottery()) {
            builder.addMessage("机器人暂未开启Bummer功能。");
            builder.addNote("机器人未开启Bummer功能。");
            return builder.build();
        }

        if (botPermissionChecker(event)) {

            if(sessionChecker(event)) return builder.build();

            //抽取倒霉蛋
            List<NormalMember> candidates = event.getGroup().getMembers().stream().filter(member -> (
                    member.getPermission().equals(MemberPermission.MEMBER))
                    &&
                    !LotteryBummerExclusion.getINSTANCE().exclusionClass.userList.contains(event.getSender().getId())
                    ).collect(Collectors.toList());
            if(candidates.isEmpty()){
                builder.addMessage("要么都是管理员要么都没有人玩Bummer了？别闹。");
                return builder.build();
            } else {
                //排除官方Bot，最好不要戳到这群“正规军”
                candidates = candidates.stream().filter(member -> !IdentityUtil.isOfficialBot(member.getId())).collect(Collectors.toList());
                if(candidates.isEmpty()){
                    builder.addMessage("你想让我去禁言官方Bot？不可以的吧。");
                    return builder.build();
                }
            }
            NormalMember victim = candidates.get(rand.nextInt(candidates.size()));

            //禁言倒霉蛋
            builder.addTask(() -> victim.mute(120));

            //如果发送者不是管理员，那么发送者也将被禁言
            if (!(senderPermissionChecker(event))) {
                builder.addTask(() -> event.getSender().mute(120));
            }

            if (victim.getId() == event.getSender().getId()) {
                builder.addMessage("Ok Bummer! " + victim.getNick() + "\n" +
                        event.getSender().getNick() + "尝试随机极限一换一。他成功把自己换出去了！");
            } else if ((senderPermissionChecker(event))) {
                //如果发送者是管理员，那么提示
                builder.addMessage(new PlainText("Ok Bummer! " + victim.getNick() + "\n管理员")
                        .plus(new At(event.getSender().getId()))
                        .plus(new PlainText(" 随机带走了 "))
                        .plus(new At(victim.getId())));
            } else {
                //如果发送者不是管理员，那么提示
                builder.addMessage(new PlainText("Ok Bummer! " + victim.getNick() + "\n")
                        .plus(new At(event.getSender().getId()))
                        .plus(new PlainText(" 以自己为代价随机带走了 "))
                        .plus(new At(victim.getId())));
            }
            BUMMER_ACTIVATION_FLAG.put(event.getGroup().getId(),event.getSender().getId(),true);
            builder.addTask(() -> EXECUTOR.schedule(() -> BUMMER_ACTIVATION_FLAG.remove(event.getGroup().getId(),event.getSender().getId()), StandardTimeUtil.getPeriodLengthInMS(0, 0, 2, 0), TimeUnit.MILLISECONDS));
            return builder.build();
        } else {
            builder.addMessage("七筒目前还没有管理员权限，请授予七筒权限解锁更多功能。");
            builder.addNote("群 " + event.getGroup().getId() + " 尝试发起Bummer功能，但该群未授予Bot管理员权限。");
            return builder.build();
        }
    }

    public static RespondTask okWinner(GroupMessageEvent event, RespondTask.Builder builder) {

        //获取当日幸运数字
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int date = calendar.get(Calendar.DATE);
        long numOfTheDay = (year + month * 10000 + date * 1000000) * 100000000000L / event.getGroup().getId();

        //获取当日幸运儿
        //排除掉官方Bot，不要去动它们
        List<NormalMember> candidates = event.getGroup().getMembers().stream().filter(member -> !IdentityUtil.isOfficialBot(member.getId())).collect(Collectors.toList());
        long guyOfTheDay = numOfTheDay % candidates.size();

        builder.addMessage("Ok Winner! " + candidates.get(Math.toIntExact(guyOfTheDay)).getNick());
        builder.addTask(() -> {
            try {
                ImageCreater.sendImage(ImageCreater.createWinnerImage(candidates.get(Math.toIntExact(guyOfTheDay))), event);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return builder.build();
    }

    public static RespondTask okC4(GroupMessageEvent event, RespondTask.Builder builder) {
        if(!GroupConfigManager.lotteryConfig(event)) {
            builder.addMessage("本群暂未开启C4功能。");
            builder.addNote("群 " + event.getGroup().getId() + " 尝试发起Bummer功能，但该群未开启该功能。");
            return builder.build();
        }

        if(!ConfigHandler.getINSTANCE().config.getGroupFC().isLottery()) {
            builder.addMessage("机器人暂未开启C4功能。");
            builder.addNote("机器人未开启C4功能。");
            return builder.build();
        }

        if (botPermissionChecker(event)) {
            if (!C4_ACTIVATION_FLAGS.getOrDefault(event.getGroup().getId(), false)) {
                double ratio = 1D / Math.sqrt(event.getGroup().getMembers().size());

                if (rand.nextDouble() < ratio) {
                    //禁言全群
                    builder.addTask(() -> event.getGroup().getSettings().setMuteAll(true));
                    builder.addMessage("中咧！");
                    builder.addMessage(new At(event.getSender().getId()).plus("成功触发了C4！大家一起恭喜TA！"));
                    C4_ACTIVATION_FLAGS.put(event.getGroup().getId(), true);

                    //设置5分钟后解禁
                    builder.addTask(() -> EXECUTOR.schedule(() -> event.getGroup().getSettings().setMuteAll(false), StandardTimeUtil.getPeriodLengthInMS(0, 0, 5, 0), TimeUnit.MILLISECONDS));

                } else {
                    builder.addMessage(new At(event.getSender().getId()).plus("没有中！"));
                }
            } else {
                builder.addMessage(new At(event.getSender().getId()).plus("今日的C4已经被触发过啦！请明天再来尝试作死！"));
            }
            return builder.build();
        } else {
            builder.addMessage("七筒目前还没有管理员权限，请授予七筒权限解锁更多功能。");
            builder.addNote("群 " + event.getGroup().getId() + " 尝试发起C4功能，但该群未授予Bot管理员权限。");
            return builder.build();
        }
    }
}