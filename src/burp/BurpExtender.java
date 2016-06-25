package burp;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * 构造sql命令(BurpSuite插件)
 * 
 * @author dream9
 *
 */
public class BurpExtender implements IBurpExtender, IMessageEditorTabFactory,
		IProxyListener {
	private IBurpExtenderCallbacks callbacks;
	private IExtensionHelpers helpers;
	private PrintWriter stdout;

	@Override
	public IMessageEditorTab createNewInstance(
			IMessageEditorController controller, boolean editable) {
		return new sqlmapHelperTab(controller);
	}

	@Override
	public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
		this.callbacks = callbacks;
		this.helpers = callbacks.getHelpers();
		this.stdout = new PrintWriter(callbacks.getStdout(), true);
		callbacks.setExtensionName("sqlmapHelper (created by dream9)"); // 扩展名称
		callbacks.registerProxyListener(this);
		callbacks.registerMessageEditorTabFactory(this);
		stdout.println("插件安装成功"); // 调试信息
	}

	@Override
	public void processProxyMessage(boolean messageIsRequest,
			IInterceptedProxyMessage message) {
	}

	/**
	 * 内部类
	 */
	public class sqlmapHelperTab implements IMessageEditorTab {
		private List<String> headers;
		private IMessageEditorController controller;

		private JPanel tabPanel;
		private JButton copy;
		private JCheckBox threads;
		private JTextField tCount;
		private ITextEditor tab = callbacks.createTextEditor();
		private byte[] request; // 用于保存原始请求
		private String cmd = ""; // 最终拼接的命令

		public sqlmapHelperTab(IMessageEditorController controller) {
			this.controller = controller;
			Component tabComponet = tab.getComponent();
			tabPanel = new JPanel();
			JPanel mid = new JPanel();
			threads = new JCheckBox("threads");
			tCount = new JTextField("10");
			mid.add(threads);
			mid.add(tCount);
			JPanel bot = new JPanel();
			copy = new JButton("复制");
			bot.add(copy);
			GridBagLayout layout = new GridBagLayout(); // GridBagLayout布局方式
			tabPanel.setLayout(layout);
			GridBagConstraints tabConstraint = new GridBagConstraints();
			tabConstraint.gridx = 0;
			tabConstraint.gridy = 0; // 控件坐标
			tabConstraint.gridwidth = 1;
			tabConstraint.gridheight = 1;
			tabConstraint.weightx = 1;
			tabConstraint.weighty = 1;
			tabConstraint.fill = GridBagConstraints.BOTH;
			tabConstraint.anchor = GridBagConstraints.NORTH;
			// 把 ITextEditor放入Panel
			tabPanel.add(tabComponet, tabConstraint);

			GridBagConstraints midConstraint = new GridBagConstraints();
			midConstraint.gridx = 0;
			midConstraint.gridy = 1;// 控件坐标
			midConstraint.gridwidth = 1;
			midConstraint.gridheight = 1;
			tabPanel.add(mid, midConstraint);

			GridBagConstraints botConstraint = new GridBagConstraints();
			botConstraint.gridx = 0;
			botConstraint.gridy = 2;// 控件坐标
			botConstraint.gridwidth = 1;
			botConstraint.gridheight = 1;
			botConstraint.insets = new Insets(0, 0, 100, 0); // 设置底部填充
			tabPanel.add(bot, botConstraint);
		}

		@Override
		public String getTabCaption() {
			return "sqlmapHelper";// 选项卡名字
		}

		@Override
		public Component getUiComponent() {
			return tabPanel;
		}

		/**
		 * 是否启用选项卡
		 */
		@Override
		public boolean isEnabled(byte[] content, boolean isRequest) {
			return isRequest;
		}

		/**
		 * 选项卡展示的内容
		 */
		@Override
		public void setMessage(byte[] content, boolean isRequest) {
			if (content == null) {
				return;
			}
			this.request = content;
			IRequestInfo req = helpers.analyzeRequest(content);

			String method = req.getMethod(); // 请求的方法类型
			List<String> headers = req.getHeaders(); // 所有请求头
			this.headers = headers;

			// httpService对象里面包含协议、主机和端口
			IHttpService httpService = controller.getHttpService();
			String pro = httpService.getProtocol(); // 协议
			String host = httpService.getHost(); // 主机
			int port = httpService.getPort(); // 端口
			String path = "";// 请求路径
			String firstHeader = headers.get(0);
			path = firstHeader.substring(firstHeader.indexOf(" ") + 1,
					firstHeader.lastIndexOf(" ")); // 请求的路径

			// 注意大小写
			String userAgent = getHeader("User-Agent");
			String cookie = getHeader("Cookie");

			String url = "";
			if (port == 80 || port == 443) {
				url = pro + "://" + host + path;
			} else {
				url = pro + "://" + host + ":" + port + path;
			}

			// 拼接命令
			String beforeCmd = "sqlmap -u \"";
			String midCmd = " --user-agent=\"" + userAgent + "\" --cookie \""
					+ cookie + "\"";
			String lasterCmd = " --batch --dbs";
			if ("GET".equals(method)) {
				cmd = beforeCmd + url + "\"" + midCmd + lasterCmd;
			} else if ("POST".equals(method)) {
				// 获取请求体
				String data = new String(content)
						.substring(req.getBodyOffset());
				cmd = beforeCmd + url + "\"" + " --data \"" + data + "\""
						+ midCmd + lasterCmd;
			}
			if (threads.isSelected()) {
				threads.setSelected(false);
			}
			// 复制按钮事件处理
			copy.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					// 复制到剪切板
					StringSelection strs = new StringSelection(cmd);
					Toolkit.getDefaultToolkit().getSystemClipboard()
							.setContents(strs, strs);
				}
			});
			// 线程数量事件处理
			threads.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String count = tCount.getText();
					try {
						int c = Integer.parseInt(count);
						if (c < 1 || c > 10) {
							count = "10";
						}
					} catch (Exception ex) {
						count = "10";
					}
					String threadStr = " --threads " + count;
					if (threads.isSelected()) {
						if (cmd.indexOf(threadStr) < 0) {
							cmd += threadStr;
						}
					} else {
						cmd = cmd.replace(threadStr, ""); // 此处存在bug(∩_∩)(需使用正则表达式匹配)
					}
					tab.setText(cmd.getBytes());
				}
			});
			// 设置面板显示的内容
			tab.setText(cmd.getBytes());
		}

		/**
		 * 重新发送的请求
		 */
		@Override
		public byte[] getMessage() {
			return request;
		}

		@Override
		public boolean isModified() {
			return tab.isTextModified();
		}

		@Override
		public byte[] getSelectedData() {
			return tab.getSelectedText();
		}

		/**
		 * 获取某一请求头内容
		 * 
		 * @param header
		 * @return
		 */
		private String getHeader(String header) {
			String value = "";
			for (String h : headers) {
				if (h.indexOf(header.trim() + ": ") > -1) {
					value = h
							.substring(h.indexOf(header) + header.length() + 2);
					break;
				}
			}
			if ("User-Agent".equals(header) && "".equals(value.trim())) {
				value = "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/49.0.2623.87 Safari/537.36";
			}
			return value;
		}

	}

}
