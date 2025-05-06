package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.List;

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
}
