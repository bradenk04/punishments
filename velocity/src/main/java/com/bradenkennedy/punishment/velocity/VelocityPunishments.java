package com.bradenkennedy.punishment.velocity;
import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.command.*;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.bradenkennedy.punishment.api.model.*;
import com.bradenkennedy.punishment.storage.JdbcPunishmentRepository;
import com.bradenkennedy.punishment.network.NetworkCache;
import com.bradenkennedy.punishment.network.PunishmentMessages;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(id="punishments", name="Punishments", version="1.0.0", authors={"Braden Kennedy"})
public final class VelocityPunishments {
    private final ProxyServer server;
    private final Logger logger;
    private final Path directory;
    private final Properties settings=new Properties();
    private JdbcPunishmentRepository repository;
    private NetworkCache cache;
    private ScheduledTask task;
    private Set<String> blocked;
    @Inject public VelocityPunishments(ProxyServer server, Logger logger, @DataDirectory Path directory) {
        this.server=server; this.logger=logger; this.directory=directory;
    }
    @Subscribe public void initialize(ProxyInitializeEvent event) throws Exception {
        Files.createDirectories(directory); Path file=directory.resolve("config.properties");
        if (!Files.exists(file)) Files.writeString(file,"database.url=\ndatabase.username=\ndatabase.password=\npoll-interval-ms=1000\nnotify-on-proxy=false\nblocked-commands=msg,tell,w,whisper,r,reply,me,teammsg\n");
        try(var input=Files.newInputStream(file)) { settings.load(input); }
        String url=settings.getProperty("database.url","");
        if (!(url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:postgresql:"))) {
            throw new IllegalArgumentException("Configure the shared MySQL/PostgreSQL database before enabling Punishments on Velocity");
        }
        repository=new JdbcPunishmentRepository(url,settings.getProperty("database.username",""),settings.getProperty("database.password",""));
        cache=new NetworkCache(repository);
        blocked=Set.copyOf(Arrays.asList(settings.getProperty("blocked-commands","msg,tell,w,whisper,r,reply,me,teammsg").toLowerCase(Locale.ROOT).split(",")));
        for(String name:List.of("ban","tempban","unban","mute","tempmute","unmute","warn","unwarn","kick","punish")) {
            server.getCommandManager().register(server.getCommandManager().metaBuilder(name).plugin(this).build(),new NetworkCommand(name));
        }
        task=server.getScheduler().buildTask(this,this::poll).repeat(Math.max(100,Long.parseLong(settings.getProperty("poll-interval-ms","1000"))),TimeUnit.MILLISECONDS).schedule();
        logger.info("Punishments network storage enabled; install the Paper companion on every backend to enforce signed-chat mutes.");
    }
    @Subscribe public EventTask login(LoginEvent event) {
        return EventTask.async(() -> {
            if(repository==null) { event.setResult(ResultedEvent.ComponentResult.denied(Component.text("Punishment database unavailable"))); return; }
            try {
                cache.load(event.getPlayer().getUniqueId());
                repository.rememberName(event.getPlayer().getUniqueId(),event.getPlayer().getUsername());
                cache.active(event.getPlayer().getUniqueId(),PunishmentType.BAN)
                    .ifPresent(p -> event.setResult(ResultedEvent.ComponentResult.denied(screen(p))));
            } catch(RuntimeException failure) { event.setResult(ResultedEvent.ComponentResult.denied(Component.text("Punishment database unavailable. Please try again."))); }
        });
    }
    @Subscribe public void disconnect(DisconnectEvent event) { if(cache!=null) cache.remove(event.getPlayer().getUniqueId()); }
    @Subscribe public void chat(PlayerChatEvent event) {
        if(cache!=null && cache.active(event.getPlayer().getUniqueId(),PunishmentType.MUTE).isPresent()
                && event.getPlayer().getProtocolVersion().getProtocol()<760) {
            event.setResult(PlayerChatEvent.ChatResult.denied()); event.getPlayer().sendMessage(Component.text("You are muted."));
        }
        // Modern signed chat must be suppressed by the Paper companion, without breaking signature validation.
    }
    @Subscribe public void command(CommandExecuteEvent event) {
        if(cache==null || !(event.getCommandSource() instanceof Player player)) return;
        String label=event.getCommand().split(" ",2)[0].toLowerCase(Locale.ROOT);
        label=label.substring(label.indexOf(':')+1);
        if(blocked.contains(label) && cache.active(player.getUniqueId(),PunishmentType.MUTE).isPresent()) {
            event.setResult(CommandExecuteEvent.CommandResult.denied()); player.sendMessage(Component.text("You are muted."));
        }
    }
    private void poll() {
        try {
            cache.poll(change -> repository.findById(change.punishmentId).ifPresent(p -> {
                if(change.action.equals("ISSUE") && p.type()==PunishmentType.KICK)
                    server.getPlayer(p.target()).ifPresent(player -> player.disconnect(screen(p)));
                if(Boolean.parseBoolean(settings.getProperty("notify-on-proxy","false"))) {
                    var text=Component.text("[Punishments] "+change.action+" "+p.type()+" "+p.target()+" ID "+p.id());
                    server.getAllPlayers().stream().filter(s -> s.hasPermission("punishments.notify")).forEach(s -> s.sendMessage(text));
                }
            }));
            for(Player player:server.getAllPlayers()) {
                cache.load(player.getUniqueId()); cache.active(player.getUniqueId(),PunishmentType.BAN).ifPresent(p -> player.disconnect(screen(p)));
            }
        } catch(RuntimeException failure) { logger.warn("Punishment network refresh failed; will retry."); }
    }
    private Component screen(Punishment p) {
        return Component.text(PunishmentMessages.render(settings.getProperty("punishment-screen", "<type>: <reason>\nID: <id>\nIssuer: <staff>\nIssued: <date>\nExpires: <expiry>"), p));
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event) throws Exception { if(task!=null) task.cancel(); if(repository!=null) repository.close(); }
    private final class NetworkCommand implements SimpleCommand {
        private final String name;
        private NetworkCommand(String name) { this.name=name; }
        @Override public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("punishments."+(name.equals("punish")?"history":name));
        }
        @Override public void execute(Invocation invocation) {
            server.getScheduler().buildTask(VelocityPunishments.this,() -> perform(invocation)).schedule();
        }
        private void perform(Invocation invocation) {
            try {
                String[] args=invocation.arguments(); int offset=name.equals("punish")?1:0;
                if(args.length<=offset || (name.equals("punish") && !args[0].equalsIgnoreCase("history"))) {
                    invocation.source().sendMessage(Component.text("Usage: /"+name+(name.equals("punish")?" history":"")+" <player UUID or online name>"+(name.startsWith("temp")?" <duration: 1h/30m>":"")+" [reason]")); return;
                }
                UUID target=server.getPlayer(args[offset]).map(Player::getUniqueId).orElseGet(() -> UUID.fromString(args[offset]));
                if(name.equals("punish")) {
                    for(var p:repository.findHistory(target)) invocation.source().sendMessage(Component.text(p.id()+" "+p.type()+" "+p.issuer().issuedAt()+" "+Objects.toString(p.reason(),"")+(p.revoked()?" revoked":p.expired()?" expired":"")));
                    return;
                }
                var online=server.getPlayer(target);
                if(!name.startsWith("un") && online.isPresent() && online.get().hasPermission("punishments.exempt")) {
                    invocation.source().sendMessage(Component.text("This player is exempt.")); return;
                }
                String kind=name.replaceFirst("^(temp|un)","");
                PunishmentType type=PunishmentType.valueOf(kind.toUpperCase(Locale.ROOT));
                UUID issuer=invocation.source() instanceof Player player?player.getUniqueId():new UUID(0,0);
                Instant now=Instant.now(); Instant expiry=null; int reasonOffset=offset+1;
                if(name.startsWith("temp")) { if(args.length<=reasonOffset) throw new IllegalArgumentException("Missing duration"); expiry=now.plus(duration(args[reasonOffset++])); }
                String reason=reasonOffset<args.length?String.join(" ",Arrays.copyOfRange(args,reasonOffset,args.length)):null;
                if(name.startsWith("un")) {
                    var active=repository.findActive(target,type);
                    if(active.isEmpty()) { invocation.source().sendMessage(Component.text("No active "+type)); return; }
                    repository.revoke(active.get().id(),issuer,reason,now);
                } else {
                    if((type==PunishmentType.BAN || type==PunishmentType.MUTE) && repository.findActive(target,type).isPresent()) {
                        invocation.source().sendMessage(Component.text("Already active: "+type)); return;
                    }
                    repository.create(new Punishment(UUID.randomUUID(),target,type,new PunishmentIssuer(issuer,now),reason,expiry,false));
                }
                invocation.source().sendMessage(Component.text("Punishment updated across the network.")); poll();
            } catch(IllegalArgumentException error) { invocation.source().sendMessage(Component.text("Use a valid player UUID, online name and positive duration (e.g. 1h).")); }
              catch(RuntimeException error) { invocation.source().sendMessage(Component.text("Database operation failed. No success is assumed.")); }
        }
        private Duration duration(String text) {
            if(!text.matches("[1-9][0-9]*[smhd]")) throw new IllegalArgumentException("Invalid duration");
            long value=Long.parseLong(text.substring(0,text.length()-1));
            return Duration.ofSeconds(Math.multiplyExact(value,switch(text.charAt(text.length()-1)){case 'm'->60L;case 'h'->3600L;case 'd'->86400L;default->1L;}));
        }
    }
}

