
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.JaroWinklerDistance;
public class SuperSearchTest {
    public static void main(String[] args) {
        System.out.println("start");
        //十万次匹配大约2S，效率还是比较不错的
        for(int i=0;i<100000;i++){
            String str1 = "kittenkittenkittenkittenkittenkittenkittendwaaaaaaaa";
            String str2 = "sittingsittingsittingsittingsittingkittenkittenkittenkittenkittenkittenkitten";

            // 计算Levenshtein距离
            LevenshteinDistance levenshtein = new LevenshteinDistance();
            int distance = levenshtein.apply(str1, str2);
            //System.out.println("Levenshtein距离: " + distance); // 输出: 3

            // 计算Jaro-Winkler距离
            JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();
            double similarity = jaroWinkler.apply(str1, str2);
            //System.out.println("Jaro-Winkler相似度: " + similarity); // 输出示例: 0.746...
        }
        System.out.println("done");
    }
}
