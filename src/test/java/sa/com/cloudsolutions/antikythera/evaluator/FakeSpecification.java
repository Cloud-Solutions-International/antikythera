package sa.com.cloudsolutions.antikythera.evaluator;

import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class FakeSpecification implements Specification<FakeEntity> {
    @Override
    @SuppressWarnings("unused")
    public Predicate toPredicate(Root<FakeEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        return null;
    }
}
