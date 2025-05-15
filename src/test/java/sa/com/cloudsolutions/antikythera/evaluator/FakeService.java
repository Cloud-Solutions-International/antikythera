package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The majority of the tests involving this class are in TestRepository and TestMockingEvaluator
 */
public class FakeService {
    @Autowired
    private FakeRepository fakeRepository;

    @Autowired
    List<IPerson> persons;

    @SuppressWarnings("unused")
    public Object saveFakeData() {
        FakeEntity fakeEntity = new FakeEntity();
        return fakeRepository.save(fakeEntity);
    }

    @SuppressWarnings("unused")
    public List<FakeEntity> searchFakeData() {
        FakeSpecification spec = new FakeSpecification();
        return fakeRepository.findAll(spec);
    }

    @SuppressWarnings("unused")
    public List<FakeEntity> searchFakeDataWithCriteria1(FakeSearchModel searchModel) {
        CrazySpecification<FakeEntity> spec = new CrazySpecification<>();
        return fakeRepository.findAll(spec.searchOrderDetails(searchModel));
    }

    @SuppressWarnings("unused")
    public List<FakeEntity> searchFakeDataWithCriteria2(FakeSearchModel searchModel) {
        return fakeRepository.findAll(new CrazySpecification<FakeEntity>().searchOrderDetails(searchModel));
    }

    @SuppressWarnings("unused")
    public void searchByName(String name) {
        List<FakeEntity> fakeEntities = fakeRepository.findAllByName(name);
        if (fakeEntities.isEmpty()) {
            System.out.print("No Matches!");
        }
        else {
            System.out.print("Found " + fakeEntities.size() + " matches!");
        }
    }

    @SuppressWarnings("unused")
    public void findById(Integer id) {
        Optional<FakeEntity> fakeEntity = fakeRepository.findById(id);
        if (fakeEntity.isPresent()) {
            System.out.print("Found!");
        }
        else {
            System.out.print("Not Found!");
        }
    }

    @SuppressWarnings("unused")
    public void autoList() {
        System.out.println("VB");
        for (IPerson person : persons) {
            System.out.print("Person: " + person.getClass());
        }
    }

    @SuppressWarnings("unused")
    public Integer castingHelper(List<Integer> l, Set<Integer> s) {
        Optional<Integer> found = fakeRepository.findByListAndSet(l, s);
        if (found.isPresent()) {
            System.out.print("Found!");
            return 1;
        }
        else {
            System.out.print("Not found!");
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public Integer findAll() {
        List<FakeEntity> fakeEntities = fakeRepository.findAll();
        System.out.print(fakeEntities.size() + "!");
        return fakeEntities.size();
    }
}
