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
}
