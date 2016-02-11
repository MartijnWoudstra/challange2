package protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test {
	public static void main(String[] args) {
		
		List<Integer> ints = new ArrayList<Integer>();
		
		ints.add(254);
		ints.add(255);
		ints.add(256);
		ints.add(2 + (1*255));
		
		for (Integer integer : ints) {
			System.out.println(integer);
		}
	}
}
