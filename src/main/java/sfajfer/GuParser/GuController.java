package sfajfer.GuParser;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/gu")
@CrossOrigin(origins = "guindex-sand.vercel.app")
public class GuController {

    @Autowired
    private GuRepository repository;

    @GetMapping("/search")
    public List<Gu> searchGu(@RequestParam(required = false) String name,
                             @RequestParam(required = false) Integer rank,
                             @RequestParam(required = false) String keyword) {
        if (name != null) return repository.findByNameContainingIgnoreCase(name);
        if (rank != null) return repository.findByRankContaining(rank);
        if (keyword != null) return repository.findByKeywordsContaining(keyword);
        return repository.findAll();
    }

    @GetMapping("/sort/health")
    public List<Gu> sortByHealth() {
        return repository.findAllByOrderByHealthDesc();
    }
}