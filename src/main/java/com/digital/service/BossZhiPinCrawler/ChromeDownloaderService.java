package com.digital.service.BossZhiPinCrawler;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ChromeDownloaderService {
    // 声明驱动
    private RemoteWebDriver driver;


    public ChromeDownloaderService() {
        // 自动下载并设置 chromedriver
        WebDriverManager webDriverManager = WebDriverManager.chromedriver();
        webDriverManager.setup();
        
        // 获取并打印 chromedriver 的实际路径
        try {
            String driverPath = webDriverManager.getDownloadedDriverPath();
            log.info("ChromeDriver 位置: {}", driverPath);
            System.out.println("[ChromeDriver] 驱动文件位置: " + driverPath);
        } catch (Exception e) {
            // 如果获取路径失败，输出默认位置信息
            String userHome = System.getProperty("user.home");
            String defaultPath = userHome + "\\.cache\\selenium\\chromedriver\\";
            log.info("ChromeDriver 可能位于: {}", defaultPath);
            System.out.println("[ChromeDriver] 驱动文件可能位于: " + defaultPath);
        }

        // 创建浏览器参数对象
        ChromeOptions chromeOptions = new ChromeOptions();
        // chromeOptions.addArguments("--headless");
        chromeOptions.addArguments("--window-size=1280,700");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
        chromeOptions.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.101 Safari/537.36");

        // 创建驱动
        this.driver = new ChromeDriver(chromeOptions);
    }

    public String download(String url) {
        try {
            driver.get(url);
            // 增加等待时间，确保页面完全加载
            Thread.sleep(5000);

            // 等待页面元素加载完成
            try {
                driver.executeScript(
                        "return new Promise((resolve) => { " +
                                "  if (document.readyState === 'complete') { " +
                                "    setTimeout(resolve, 2000); " +
                                "  } else { " +
                                "    window.addEventListener('load', () => setTimeout(resolve, 2000)); " +
                                "  } " +
                                "});"
                );
            } catch (Exception e) {
                // 如果Promise失败，继续等待
                Thread.sleep(3000);
            }

            // 滚动页面，触发懒加载
            try {
                driver.executeScript(
                        "window.scrollTo(0, document.body.scrollHeight / 2);"
                );
                Thread.sleep(2000);
                driver.executeScript(
                        "window.scrollTo(0, document.body.scrollHeight - 1000);"
                );
                Thread.sleep(2000);
            } catch (Exception e) {
                // 如果滚动失败，继续执行
            }

            // 获取页面源代码
            String pageSource = driver.getPageSource();

            return pageSource;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }
}