package sfajfer.GuParser;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuRepository extends MongoRepository<Gu, String> {
    
    // Search by Name (Case Insensitive)
    List<Gu> findByNameContainingIgnoreCase(String name);

    // Filter by a specific Rank in the array
    List<Gu> findByRankContaining(Integer rank);

    // Filter by Keyword
    List<Gu> findByKeywordsContaining(String keyword);
    
    // Sort results by Health descending
    List<Gu> findAllByOrderByHealthDesc();
}