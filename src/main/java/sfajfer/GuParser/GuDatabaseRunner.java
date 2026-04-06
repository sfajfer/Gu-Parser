package sfajfer.GuParser;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class GuDatabaseRunner implements CommandLineRunner {

    private final GuParser parserService;

    public GuDatabaseRunner(GuParser parserService) {
        this.parserService = parserService;
    }

    @Override
    public void run(String... args) throws Exception {
        String pathToFile = "../Gu Index.md"; 
        parserService.parseAndPopulate(pathToFile);
    }
}