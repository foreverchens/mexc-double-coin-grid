package com.grid.mxc.api;

import com.grid.mxc.api.impl.GridCmd;
import com.grid.mxc.api.impl.PriceCmd;
import com.grid.mxc.api.impl.RestartCmd;
import com.grid.mxc.common.MxcClient;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * @author yyy
 */
@Slf4j
public class TelegramBotService implements UpdatesListener {

	private String chatId;
	private TelegramBot bot;
	private static Map<String, BotCmdHandleFunc> cmdHandleFuncMap = new HashMap<>();

	public TelegramBotService() {
		InputStream resourceAsStream = MxcClient.class.getResourceAsStream("/application-grid.yaml");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// 先扫在启动机器人
		scanCmd();
		String token = properties.getProperty("token");
		chatId = properties.getProperty("chatId");
		bot = new TelegramBot(token);
		bot.setUpdatesListener(this);
	}

	public static void scanCmd() {
		cmdHandleFuncMap.put("/grid", new GridCmd());
		cmdHandleFuncMap.put("/p", new PriceCmd());
		cmdHandleFuncMap.put("/restart", new RestartCmd());
	}

	@Override
	public int process(List<Update> updates) {
		updates.forEach(update -> {
			log.info("机器人收到消息 -> {}", update);

			Message message = update.message();
			if (Objects.isNull(message)) {
				return;
			}
			String text = message.text();
			if (!StringUtils.startsWith(text, "/")) {
				return;
			}
			text = text.toLowerCase(Locale.ROOT);
			String key = text.split(":")[0];
			BotCmdHandleFunc func = cmdHandleFuncMap.get(key);
			if (Objects.isNull(func)) {
				this.sendMessage(false, "未能识别的指令、可输入/help查看可用指令");
				return;
			}
			String res = func.handle(text);
			log.error("res:{}", res);
			boolean markdown = Arrays.asList("/help", "/grid").contains(key);
			this.sendMessage(markdown, res);
		});
		return UpdatesListener.CONFIRMED_UPDATES_ALL;
	}

	/**
	 * 发送消息
	 *
	 * @param text 消息内容
	 */
	public Message sendMessage(boolean markdown, String text) {
		if (markdown) {
			text = markdownHandle(text);
		}
		SendMessage message = new SendMessage(chatId, text);
		message.disableWebPagePreview(true);
		if (markdown) {
			message.parseMode(ParseMode.MarkdownV2);
		}
		SendResponse response = bot.execute(message);
		if (response.isOk()) {
			return response.message();
		}
		return sendMessage(markdown, response.description());
	}

	private String markdownHandle(String text) {
		if (StringUtils.isBlank(text)) {
			return text;
		}
		return text.replace(".", "\\.").replace("-", "\\-").replace("=", "\\=").replace("[", "\\[").replace("]", "\\]").replace("(", "\\(").replace(")", "\\)").replace("|", "\\|");
	}
}


