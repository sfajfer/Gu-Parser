package sfajfer.GuParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class GuDatabaseRunner implements CommandLineRunner {
    @Autowired
    private GuParser guParser;

    @Override
    public void run(String... args) throws Exception {
        // Pass only the filename
        guParser.parseAndPopulate("Gu Index.md");
    }
}