package bms.player.beatoraja.skin.scene;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneAllocationTest {
	@Test
	void warmedEvaluatorDoesNotAllocatePerFrame() throws Exception {
		java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
		Assumptions.assumeTrue(platformBean instanceof com.sun.management.ThreadMXBean);
		com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) platformBean;
		Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
		bean.setThreadAllocatedMemoryEnabled(true);

		Path scenePath = Path.of("..", "examples", "skin-scene",
				"projective-grid.scene.json").toAbsolutePath().normalize();
		SceneDocument document = new SceneDocumentLoader().load(scenePath);
		SceneEvaluator evaluator = new SceneEvaluator(new SceneCompiler().compile(document, scenePath));
		for (int i = 0; i < 20_000; i++) {
			evaluator.evaluate(i % 120_000, 0, 0, 320, 180, 0, 1, 1, 1, 1);
		}

		long thread = Thread.currentThread().getId();
		long before = bean.getThreadAllocatedBytes(thread);
		for (int i = 0; i < 10_000; i++) {
			evaluator.evaluate(i % 120_000, 0, 0, 320, 180, 0, 1, 1, 1, 1);
		}
		long allocated = bean.getThreadAllocatedBytes(thread) - before;
		assertEquals(0, allocated, "warmed scene evaluation allocated heap bytes");
	}
}
