package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.drops.DiscordWebhookService;
import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.drops.SupabaseDropService;
import com.github.orgonag.fbclan.lfg.LfgService;
import com.github.orgonag.fbclan.panel.DropLogPanel;
import com.github.orgonag.fbclan.panel.FinalBossPanel;
import com.github.orgonag.fbclan.panel.LfgPanel;
import com.github.orgonag.fbclan.panel.LockedPanel;
import com.github.orgonag.fbclan.wom.WomVerificationService;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import com.google.inject.Provides;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "Final Boss",
    description = "Clan tools for Final Boss — drop logging, LFG, and more",
    tags = {"clan", "final boss", "drops", "lfg", "looking for group"},
    enabledByDefault = false
)
public class FinalBossPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private FinalBossConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ScheduledExecutorService executor;

    private NavigationButton navButton;
    private LockedPanel lockedPanel;
    private FinalBossPanel mainPanel;

    private WomVerificationService womService;
    private SupabaseDropService dropService;
    private DiscordWebhookService discordService;
    private LfgService lfgService;

    private DropLogPanel dropLogPanel;
    private LfgPanel lfgPanel;

    private volatile boolean verified = false;
    private volatile String currentRsn = null;
    private ScheduledFuture<?> lfgPollFuture;
    private ScheduledFuture<?> dropRefreshFuture;

    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    @Override
    protected void startUp()
    {
        womService = new WomVerificationService(okHttpClient);
        dropService = new SupabaseDropService(okHttpClient);
        discordService = new DiscordWebhookService(okHttpClient);
        lfgService = new LfgService(okHttpClient);

        dropLogPanel = new DropLogPanel(dropService, executor);
        lfgPanel = new LfgPanel(lfgService, executor);

        lockedPanel = new LockedPanel();
        mainPanel = new FinalBossPanel(dropLogPanel, lfgPanel);

        lockedPanel.setRetryAction(e -> verifyMembership());

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(icon)
            .priority(10)
            .panel(lockedPanel)
            .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        if (currentRsn != null && config.enableLfg())
        {
            executor.submit(() -> lfgService.removeStatus(currentRsn));
        }

        if (lfgPollFuture != null)
        {
            lfgPollFuture.cancel(true);
            lfgPollFuture = null;
        }
        if (dropRefreshFuture != null)
        {
            dropRefreshFuture.cancel(true);
            dropRefreshFuture = null;
        }

        clientToolbar.removeNavigation(navButton);
        verified = false;
        currentRsn = null;
        womService.clearCache();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() -> {
                if (client.getLocalPlayer() != null)
                {
                    String rsn = client.getLocalPlayer().getName();
                    currentRsn = rsn;
                    SwingUtilities.invokeLater(() -> lfgPanel.setCurrentRsn(rsn));
                    verifyMembership();
                }
            });
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            verified = false;
            currentRsn = null;
            womService.clearCache();
            stopPolling();
            SwingUtilities.invokeLater(() -> {
                clientToolbar.removeNavigation(navButton);
                navButton = NavigationButton.builder()
                    .tooltip("Final Boss")
                    .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
                    .priority(10)
                    .panel(lockedPanel)
                    .build();
                clientToolbar.addNavigation(navButton);
                lockedPanel.showVerifying();
            });
        }
    }

    private void verifyMembership()
    {
        SwingUtilities.invokeLater(() -> lockedPanel.showVerifying());

        executor.submit(() -> {
            try
            {
                boolean isMember = womService.verify(currentRsn);
                if (isMember)
                {
                    verified = true;
                    SwingUtilities.invokeLater(this::showMainPanel);
                    startPolling();
                }
                else
                {
                    SwingUtilities.invokeLater(() -> lockedPanel.showNotMember());
                }
            }
            catch (Exception e)
            {
                log.warn("WOM verification failed", e);
                SwingUtilities.invokeLater(() -> lockedPanel.showError());
            }
        });
    }

    private void showMainPanel()
    {
        clientToolbar.removeNavigation(navButton);
        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(mainPanel)
            .build();
        clientToolbar.addNavigation(navButton);
        mainPanel.refreshActiveTab();
    }

    private void startPolling()
    {
        if (config.enableLfg())
        {
            lfgPollFuture = executor.scheduleAtFixedRate(() -> {
                try { lfgPanel.refresh(); }
                catch (Exception e) { log.warn("LFG poll error", e); }
            }, 30, 30, TimeUnit.SECONDS);
        }

        if (config.enableDropLogging())
        {
            dropRefreshFuture = executor.scheduleAtFixedRate(() -> {
                try { dropLogPanel.refresh(); }
                catch (Exception e) { log.warn("Drop refresh error", e); }
            }, 60, 60, TimeUnit.SECONDS);
        }
    }

    private void stopPolling()
    {
        if (lfgPollFuture != null)
        {
            lfgPollFuture.cancel(true);
            lfgPollFuture = null;
        }
        if (dropRefreshFuture != null)
        {
            dropRefreshFuture.cancel(true);
            dropRefreshFuture = null;
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (!verified || !config.enableDropLogging())
        {
            return;
        }

        String npcName = event.getNpc().getName();

        for (ItemStack itemStack : event.getItems())
        {
            int itemId = itemStack.getId();
            int quantity = itemStack.getQuantity();
            int gePrice = itemManager.getItemPrice(itemId);

            if (DropTrackingService.isValuableDrop(gePrice, quantity))
            {
                ItemComposition itemComp = itemManager.getItemComposition(itemId);
                String itemName = itemComp.getName();
                long totalValue = (long) gePrice * quantity;

                executor.submit(() -> {
                    dropService.logDrop(currentRsn, npcName, itemName, itemId, totalValue, quantity);

                    String webhookUrl = config.discordWebhookUrl();
                    discordService.sendDropNotification(webhookUrl, currentRsn, itemName, totalValue, npcName);
                });
            }
        }
    }
}
