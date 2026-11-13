package at.fhtw.docflow.identity.application;

public interface AdminBootstrapService {

    boolean bootstrap(String username, String email, String password);
}
