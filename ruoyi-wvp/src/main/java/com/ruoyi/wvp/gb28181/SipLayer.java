package com.ruoyi.wvp.gb28181;


import com.ruoyi.wvp.conf.SipConfig;
import com.ruoyi.wvp.conf.UserSetting;
import com.ruoyi.wvp.gb28181.bean.GbStringMsgParserFactory;
import com.ruoyi.wvp.gb28181.conf.DefaultProperties;
import com.ruoyi.wvp.gb28181.transmit.ISIPProcessorObserver;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.PreDestroy;
import javax.sip.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Order(value=10)
public class SipLayer implements CommandLineRunner, DisposableBean {

	@Autowired
	private SipConfig sipConfig;

	@Autowired
	private ISIPProcessorObserver sipProcessorObserver;

	@Autowired
	private UserSetting userSetting;

	private SipStackImpl sipStack;
	private final Map<String, SipProviderImpl> tcpSipProviderMap = new ConcurrentHashMap<>();
	private final Map<String, SipProviderImpl> udpSipProviderMap = new ConcurrentHashMap<>();
	private final List<String> monitorIps = new ArrayList<>();

	/**
	 * 项目启动时初始化SIP服务并监听配置的网络接口与端口
	 *
	 * @param args 启动参数
	 */
	@Override
	public void run(String... args) {
		// 启动前先释放可能残留的底层协议栈资源，防止同 JVM 内热重启出现端口绑定冲突
		destroy();
		if (ObjectUtils.isEmpty(sipConfig.getIp())) {
			try {
				// 获得本机的所有网络接口
				Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
				while (nifs.hasMoreElements()) {
					NetworkInterface nif = nifs.nextElement();
					// 获得与该网络接口绑定的 IP 地址，一般只有一个
					Enumeration<InetAddress> addresses = nif.getInetAddresses();
					while (addresses.hasMoreElements()) {
						InetAddress addr = addresses.nextElement();
						if (addr instanceof Inet4Address) {
							if (addr.getHostAddress().equals("127.0.0.1")){
								continue;
							}
							if (nif.getName().startsWith("docker")) {
								continue;
							}
							log.info("[自动配置SIP监听网卡] 网卡接口地址： {}", addr.getHostAddress());// 只关心 IPv4 地址
							monitorIps.add(addr.getHostAddress());
						}
					}
				}
			}catch (Exception e) {
				log.error("[读取网卡信息失败]", e);
			}
			if (monitorIps.isEmpty()) {
				log.error("[自动配置SIP监听网卡信息失败]， 请手动配置SIP.IP后重新启动");
				System.exit(1);
			}
		}else {
			// 使用逗号分割多个ip
			String separator = ",";
			if (sipConfig.getIp().indexOf(separator) > 0) {
				String[] split = sipConfig.getIp().split(separator);
				monitorIps.addAll(Arrays.asList(split));
			}else {
				monitorIps.add(sipConfig.getIp());
			}
		}
		if (ObjectUtils.isEmpty(sipConfig.getShowIp())){
			sipConfig.setShowIp(String.join(",", monitorIps));
		}
		SipFactory.getInstance().setPathName("gov.nist");
		if (monitorIps.size() > 0) {
			for (String monitorIp : monitorIps) {
				addListeningPoint(monitorIp, sipConfig.getPort());
			}
			if (udpSipProviderMap.size() + tcpSipProviderMap.size() == 0) {
				log.error("[SIP SERVER] 所有网卡的 SIP 监听均启动失败，请检查端口是否被占用或者配置是否正确");
				System.exit(1);
			}
		}
	}

	/**
	 * 添加指定 IP 与端口的 SIP 监听点（支持 TCP 与 UDP 双协议）
	 *
	 * @param monitorIp 监听的本地网卡 IP 地址
	 * @param port      监听端口
	 */
	private void addListeningPoint(String monitorIp, int port){
		try {
			if (sipStack == null) {
				SipFactory.getInstance().setPathName("gov.nist");
				sipStack = (SipStackImpl) SipFactory.getInstance().createSipStack(DefaultProperties.getProperties("GB28181_SIP", userSetting.getSipLog()));
				sipStack.setMessageParserFactory(new GbStringMsgParserFactory());
			}
		} catch (PeerUnavailableException e) {
			log.error("[SIP SERVER] SIP服务启动失败， 监听地址{}初始化协议栈失败,请检查配置是否正确", monitorIp, e);
			return;
		}

		try {
			ListeningPoint tcpListeningPoint = sipStack.createListeningPoint(monitorIp, port, "TCP");
			SipProviderImpl tcpSipProvider = (SipProviderImpl)sipStack.createSipProvider(tcpListeningPoint);

			tcpSipProvider.setDialogErrorsAutomaticallyHandled();
			tcpSipProvider.addSipListener(sipProcessorObserver);
			tcpSipProviderMap.put(monitorIp, tcpSipProvider);
			log.info("[SIP SERVER] tcp://{}:{} 启动成功", monitorIp, port);
		} catch (TransportNotSupportedException
				 | TooManyListenersException
				 | ObjectInUseException
				 | InvalidArgumentException e) {
			log.error("[SIP SERVER] tcp://{}:{} SIP服务启动失败,请检查端口是否被占用或者ip是否正确", monitorIp, port, e);
		}

		try {
			ListeningPoint udpListeningPoint = sipStack.createListeningPoint(monitorIp, port, "UDP");

			SipProviderImpl udpSipProvider = (SipProviderImpl)sipStack.createSipProvider(udpListeningPoint);
			udpSipProvider.addSipListener(sipProcessorObserver);
			udpSipProvider.setDialogErrorsAutomaticallyHandled();
			udpSipProviderMap.put(monitorIp, udpSipProvider);

			log.info("[SIP SERVER] udp://{}:{} 启动成功", monitorIp, port);
		} catch (TransportNotSupportedException
				 | TooManyListenersException
				 | ObjectInUseException
				 | InvalidArgumentException e) {
			log.error("[SIP SERVER] udp://{}:{} SIP服务启动失败,请检查端口是否被占用或者ip是否正确", monitorIp, port, e);
		}
	}

	/**
	 * 释放 SIP 协议栈与网络监听端口，防止热重载或停机时端口泄漏与冲突
	 */
	@Override
	@PreDestroy
	public synchronized void destroy() {
		log.info("[SIP SERVER] 正在关闭 SIP 服务并释放监听端口与协议栈资源...");

		// 1. 移除 TCP 监听器与 Provider
		for (Map.Entry<String, SipProviderImpl> entry : tcpSipProviderMap.entrySet()) {
			try {
				SipProviderImpl provider = entry.getValue();
				if (provider != null) {
					provider.removeSipListener(sipProcessorObserver);
					if (sipStack != null) {
						sipStack.deleteSipProvider(provider);
					}
				}
			} catch (Exception e) {
				log.error("[SIP SERVER] 释放 TCP SipProvider 失败: {}", entry.getKey(), e);
			}
		}
		tcpSipProviderMap.clear();

		// 2. 移除 UDP 监听器与 Provider
		for (Map.Entry<String, SipProviderImpl> entry : udpSipProviderMap.entrySet()) {
			try {
				SipProviderImpl provider = entry.getValue();
				if (provider != null) {
					provider.removeSipListener(sipProcessorObserver);
					if (sipStack != null) {
						sipStack.deleteSipProvider(provider);
					}
				}
			} catch (Exception e) {
				log.error("[SIP SERVER] 释放 UDP SipProvider 失败: {}", entry.getKey(), e);
			}
		}
		udpSipProviderMap.clear();

		// 3. 删除剩余 ListeningPoint 并停止 SipStack
		if (sipStack != null) {
			try {
				Iterator<?> lpIterator = sipStack.getListeningPoints();
				if (lpIterator != null) {
					List<ListeningPoint> pointsToDelete = new ArrayList<>();
					while (lpIterator.hasNext()) {
						pointsToDelete.add((ListeningPoint) lpIterator.next());
					}
					for (ListeningPoint lp : pointsToDelete) {
						try {
							sipStack.deleteListeningPoint(lp);
						} catch (Exception ex) {
							log.warn("[SIP SERVER] 删除监听点失败: {}:{}", lp.getIPAddress(), lp.getPort(), ex);
						}
					}
				}
				sipStack.stop();
				log.info("[SIP SERVER] SipStack 已成功停止");
			} catch (Exception e) {
				log.error("[SIP SERVER] 停止 SipStack 异常", e);
			} finally {
				sipStack = null;
			}
		}

		// 4. 重置 SipFactory，清除内部单例缓存
		try {
			SipFactory.getInstance().resetFactory();
		} catch (Exception e) {
			log.error("[SIP SERVER] 重置 SipFactory 失败", e);
		}

		monitorIps.clear();
		log.info("[SIP SERVER] SIP 服务资源已完全释放");
	}

	/**
	 * 获取指定网卡 IP 对应的 UDP SipProvider
	 *
	 * @param ip 网卡 IP 地址
	 * @return SipProviderImpl 实例，若未匹配或未启动则返回 null
	 */
	public SipProviderImpl getUdpSipProvider(String ip) {
		if (udpSipProviderMap.size() == 1) {
			return udpSipProviderMap.values().stream().findFirst().orElse(null);
		}
		if (ObjectUtils.isEmpty(ip)) {
			return null;
		}
		return udpSipProviderMap.get(ip);
	}

	/**
	 * 获取唯一的 UDP SipProvider（单网卡监听时有效）
	 *
	 * @return SipProviderImpl 实例，若存在多个监听网卡或无监听点则返回 null
	 */
	public SipProviderImpl getUdpSipProvider() {
		if (udpSipProviderMap.size() != 1) {
			return null;
		}
		return udpSipProviderMap.values().stream().findFirst().orElse(null);
	}

	/**
	 * 获取唯一的 TCP SipProvider（单网卡监听时有效）
	 *
	 * @return SipProviderImpl 实例，若存在多个监听网卡或无监听点则返回 null
	 */
	public SipProviderImpl getTcpSipProvider() {
		if (tcpSipProviderMap.size() != 1) {
			return null;
		}
		return tcpSipProviderMap.values().stream().findFirst().orElse(null);
	}

	/**
	 * 获取指定网卡 IP 对应的 TCP SipProvider
	 *
	 * @param ip 网卡 IP 地址
	 * @return SipProviderImpl 实例，若未匹配或未启动则返回 null
	 */
	public SipProviderImpl getTcpSipProvider(String ip) {
		if (tcpSipProviderMap.size() == 1) {
			return tcpSipProviderMap.values().stream().findFirst().orElse(null);
		}
		if (ObjectUtils.isEmpty(ip)) {
			return null;
		}
		return tcpSipProviderMap.get(ip);
	}

	/**
	 * 根据设备建议的本地 IP 或监听点信息获取最合适的本地 SIP IP
	 *
	 * @param deviceLocalIp 设备上报的本地地址（可为空）
	 * @return 本地监听 IP 地址
	 */
	public String getLocalIp(String deviceLocalIp) {
		if (monitorIps.size() == 1) {
			return monitorIps.get(0);
		}
		if (!ObjectUtils.isEmpty(deviceLocalIp)) {
			return deviceLocalIp;
		}
		SipProviderImpl udpProvider = getUdpSipProvider();
		if (udpProvider != null && udpProvider.getListeningPoint() != null) {
			return udpProvider.getListeningPoint().getIPAddress();
		}
		return !monitorIps.isEmpty() ? monitorIps.get(0) : "127.0.0.1";
	}
}
