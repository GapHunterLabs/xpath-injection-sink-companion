import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.springframework.web.bind.annotation.GetMapping;

class UserController {
    @GetMapping("/user")
    Object findUser(String username) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.evaluate("//user[username='" + username + "']", (Object) null);
    }
}
