package sa.com.cloudsolutions.antikythera.evaluator;

import java.io.Serializable;

public interface IContact extends IPerson, Serializable {
    public String getPhone();
    public void setPhone(String phone);
}
