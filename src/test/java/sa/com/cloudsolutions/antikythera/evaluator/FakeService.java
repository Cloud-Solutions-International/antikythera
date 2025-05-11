package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.List;
import java.util.Optional;

public class FakeService {
    @Autowired
    private FakeRepository fakeRepository;

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
    public List<FakeEntity> searchFakeDataWithCriteria(FakeSearchModel searchModel) {
        CrazySpecification<FakeEntity> spec = new CrazySpecification<>();
        return fakeRepository.findAll(spec.searchOrderDetails(searchModel));
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
}
