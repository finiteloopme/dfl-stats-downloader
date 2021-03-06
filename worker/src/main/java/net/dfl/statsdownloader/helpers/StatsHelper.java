package net.dfl.statsdownloader.helpers;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.opencsv.CSVWriter;

import net.dfl.statsdownloader.model.Fixture;
import net.dfl.statsdownloader.model.PlayerStats;
import net.dfl.statsdownloader.model.Round;
import net.dfl.statsdownloader.model.RoundStats;
import net.dfl.statsdownloader.model.TeamStats;

public class StatsHelper {
	
	private String round;
	private String year;
	
	private static final String chromeBin = System.getenv("GOOGLE_CHROME_BIN");
		
	public StatsHelper(String round, String year) {
		this.round = round;
		this.year = year;
	}
	
	private WebDriver getWebDriver() {
		WebDriver webDriver;
		
		String headlessBrowser = System.getenv("HEADLESS_BROWSER");
		if(headlessBrowser.equalsIgnoreCase("phantomjs")) {
			DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();			
			capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, new String[] { "--load-images=no", "--webdriver-loglevel=NONE" });
			webDriver = new PhantomJSDriver(capabilities);
		} else if(headlessBrowser.equalsIgnoreCase("htmlunit")) {
			webDriver = new HtmlUnitDriver(BrowserVersion.CHROME) {
		        @Override
		        protected WebClient newWebClient(BrowserVersion version) {
		            WebClient webClient = super.newWebClient(version);
		            webClient.getOptions().setThrowExceptionOnScriptError(false);
		            webClient.getOptions().setCssEnabled(false);
		            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		            return webClient;
		        }
			};
		} else {
			ChromeOptions options = new ChromeOptions();
			options.setBinary(chromeBin);
			options.addArguments("--headless", "--disable-gpu", "--no-sandbox --incognito");
			
			webDriver = new ChromeDriver(options);
		}
		
		return webDriver;
	}
		
	public Path execute() throws Exception {
		Round roundFixtures = getRound();
		RoundStats roundStats = createStats(roundFixtures);
		Path csvFile = writeStatsCsv(roundStats);
				
		return csvFile;
	}
	
	private Round getRound() {
		
		String paddedRoundNo = "";
		
		if(round.length() < 2) {
			paddedRoundNo = "0" + round;
		} else {
			paddedRoundNo = round;
		}
		
		String fixtureUrl = "http://www.afl.com.au/fixture?roundId=CD_R" + year + "014" + paddedRoundNo + "#tround";

		WebDriver webDriver = getWebDriver();
		webDriver.get(fixtureUrl);
		
		List<WebElement> webFixtures = webDriver.findElement(By.id("tround")).findElement(By.tagName("tbody")).findElements(By.className("team-logos"));
		
		Round roundFixutres = new Round();
		List<Fixture> games = new ArrayList<Fixture>();
		
		for(WebElement webFixture : webFixtures) {
			Fixture fixture = new Fixture();
			fixture.setHomeTeam(webFixture.findElements(By.className("home")).get(0).getText());
			fixture.setAwayTeam(webFixture.findElements(By.className("away")).get(0).getText());
						
			games.add(fixture);
		}
		
		roundFixutres.setGames(games);
		
		webDriver.quit();
		
		return roundFixutres;
	}
	
	private RoundStats createStats(Round roundFixutres) {
		
		RoundStats roundStats = new RoundStats();
		List<TeamStats> stats = new ArrayList<TeamStats>();
				
		String statsUri = "http://www.afl.com.au/match-centre/" + year + "/" + round + "/";
		
		for(Fixture game : roundFixutres.getGames()) {
			String gameStr = game.getHomeTeam().toLowerCase() + "-v-" + game.getAwayTeam().toLowerCase();
			String statsUrl = statsUri + gameStr;
			
			List<TeamStats> teamStats = downloadStats(game.getHomeTeam().toLowerCase(), game.getHomeTeam().toLowerCase(), statsUrl);
			stats.addAll(teamStats);
		}
		
		roundStats.setRoundStats(stats);
		
		return roundStats;
	}
	

	
	private List<TeamStats> downloadStats(String homeTeam, String awayTeam, String url) {
						
		List<TeamStats> stats = new ArrayList<TeamStats>();
					
		try {
			TeamStats homeTeamStats = new TeamStats();
			homeTeamStats.setTeamId(homeTeam);
			homeTeamStats.setTeamStats(getStats(url, "h"));
			
			TeamStats awayTeamStats = new TeamStats();
			awayTeamStats.setTeamId(awayTeam);
			awayTeamStats.setTeamStats(getStats(url, "a"));
						
			stats.add(homeTeamStats);
			stats.add(awayTeamStats);			
		} catch (Exception e) {} finally {} //ignore errors
		
		return stats;
	}
	
	private List<PlayerStats> getStats(String url, String homeORaway) throws Exception {
		
		List<PlayerStats> teamStats = new ArrayList<PlayerStats>();
		
		WebDriver webDriver = getWebDriver();
		webDriver.get(url);
		
		webDriver.findElement(By.cssSelector("a[href='#full-time-stats']")).click();
		webDriver.findElement(By.cssSelector("a[href='#advanced-stats']")).click();

		List<WebElement> statsRecs;
		
		if(homeORaway.equals("h")) {
			statsRecs = webDriver.findElement(By.id("homeTeam-advanced")).findElement(By.tagName("tbody")).findElements(By.tagName("tr"));
		} else {
			statsRecs = webDriver.findElement(By.id("awayTeam-advanced")).findElement(By.tagName("tbody")).findElements(By.tagName("tr"));
		}
		
		for(WebElement statsRec : statsRecs) {
			PlayerStats playerStats = new PlayerStats();
			
			List<WebElement> stats = statsRec.findElements(By.tagName("td"));

			playerStats.setName(stats.get(0).findElements(By.tagName("span")).get(1).getText());
			playerStats.setKicks(stats.get(2).getText());
			playerStats.setHandballs(stats.get(3).getText());
			playerStats.setDisposals(stats.get(4).getText());
			playerStats.setMarks(stats.get(9).getText());
			playerStats.setHitouts(stats.get(12).getText());
			playerStats.setFreesFor(stats.get(17).getText());
			playerStats.setFreesAgainst(stats.get(18).getText());
			playerStats.setTackles(stats.get(19).getText());
			playerStats.setGoals(stats.get(23).getText());
			playerStats.setBehinds(stats.get(24).getText());
			teamStats.add(playerStats);
		}
		
		webDriver.quit();
		
		return teamStats;
	}
	
	private Path writeStatsCsv(RoundStats roundStats) throws Exception {
		Path dir = Paths.get("stats");
		if(!Files.exists(dir)) {
			Files.createDirectories(dir);
		}
		
		String roundPadded = "";
		
		if(this.round.length() < 2) {
			roundPadded = "0" + this.round;
		} else {
			roundPadded = this.round;
		}
		
		DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		String now = df.format(new Date());
		
		Path csvFile = dir.resolve("stats-" + this.year + "-" + roundPadded + "_" + now + ".csv");
		
		Files.createDirectories(dir);
		CSVWriter csvFileWriter = new CSVWriter(Files.newBufferedWriter(csvFile, Charset.forName("Cp1252"), new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.WRITE}));
		
		List<String[]> rows = new ArrayList<String[]>();
		
		for(TeamStats teamStats : roundStats.getRoundStats()) {
			for(PlayerStats playerStats : teamStats.getTeamStats()) {

				rows.add(new String[]{playerStats.getName(),
									  playerStats.getDisposals(), 
									  playerStats.getMarks(),  
									  playerStats.getHitouts(), 
									  playerStats.getFreesFor(), 
									  playerStats.getFreesAgainst(),
									  playerStats.getTackles(),
									  playerStats.getGoals()});
			}
		}
		
		csvFileWriter.writeAll(rows);
		csvFileWriter.flush();
		csvFileWriter.close();

		return csvFile;
	}
}
