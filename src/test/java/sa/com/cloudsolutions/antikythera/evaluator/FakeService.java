package sa.com.cloudsolutions.antikythera.evaluator;

public class FakeService {
    @Autowired
    private FakeRepository fakeRepository;


    public Object saveFakeData() {
        FakeEntity fakeEntity = new FakeEntity();
        return fakeRepository.save(fakeEntity);
    }
}
