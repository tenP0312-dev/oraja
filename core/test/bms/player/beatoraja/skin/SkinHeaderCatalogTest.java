package bms.player.beatoraja.skin;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.SkinConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkinHeaderCatalogTest {
	@TempDir Path directory;

	static class CountingCatalog extends SkinHeaderCatalog {
		int reads;
		@Override SkinHeader loadHeader(Path path, Config config) throws IOException {
			reads++;
			return super.loadHeader(path, config);
		}
	}

	@Test void repeatedConsumersReuseDefinitionsButHaveIndependentOptionsFilesOffsetsAndCategories() throws Exception {
		Files.writeString(directory.resolve("skin.luaskin"), """
			return { type=0, name='Test', author='Author',
			 category={{name='Settings', item={'options','files','offsets'}}},
			 property={{name='Choice', category='options', def='B', item={{name='A',op=10},{name='B',op=11}}}},
			 filepath={{name='Picture',category='files',path='*.png',def='a.png'}},
			 offset={{name='Position',category='offsets',id=42,x=true}} }
			""");
		CountingCatalog catalog = new CountingCatalog();
		Config config = new Config();
		SkinHeader first = catalog.load(directory, config).get(0);
		SkinConfig.Property properties = new SkinConfig.Property();
		SkinConfig.Option choice = new SkinConfig.Option();
		choice.name = "Choice"; choice.value = 10;
		properties.setOption(new SkinConfig.Option[]{choice});
		SkinConfig.FilePath file = new SkinConfig.FilePath();
		file.name = "Picture"; file.path = "chosen.png";
		properties.setFile(new SkinConfig.FilePath[]{file});
		SkinConfig.Offset offset = new SkinConfig.Offset();
		offset.name = "Position"; offset.x = 37;
		properties.setOffset(new SkinConfig.Offset[]{offset});
		first.setSkinConfigProperty(properties);
		first.setName("Changed");
		first.getCustomOptions()[0].contents[0] = "Mutated";
		first.getCustomCategories()[0].items[0] = null;

		SkinHeader next = catalog.load(directory, config).get(0);
		assertEquals(1, catalog.reads, "F5 and F12 must not execute unchanged definitions again");
		assertEquals("Test", next.getName());
		assertEquals("A", next.getCustomOptions()[0].contents[0]);
		assertEquals(11, next.getCustomOptions()[0].getDefaultOption());
		assertEquals(SkinProperty.OPTION_RANDOM_VALUE, next.getCustomOptions()[0].getSelectedOption());
		assertNull(next.getCustomFiles()[0].getSelectedFilename());
		assertNull(next.getCustomOffsets()[0].getOffset());
		assertSame(next.getCustomOptions()[0], next.getCustomCategories()[0].items[0]);
		assertSame(next.getCustomFiles()[0], next.getCustomCategories()[0].items[1]);
		assertSame(next.getCustomOffsets()[0], next.getCustomCategories()[0].items[2]);
	}

	@Test void editsToRequiredModulesAndAddedOrDeletedSkinsInvalidateTheSharedList() throws Exception {
		Path module = directory.resolve("header.lua");
		Files.writeString(module, "return {type=0,name='Before'}");
		Files.writeString(directory.resolve("skin.luaskin"), "return require('header')");
		CountingCatalog catalog = new CountingCatalog();
		Config config = new Config();
		assertEquals("Before", catalog.load(directory, config).get(0).getName());
		Files.writeString(module, "return {type=0,name='After edit'}");
		assertEquals("After edit", catalog.load(directory, config).get(0).getName());
		Files.writeString(directory.resolve("other.json"), "{\"type\":1,\"name\":\"Other\"}");
		assertEquals(2, catalog.load(directory, config).size());
		Files.delete(directory.resolve("skin.luaskin"));
		assertEquals("Other", catalog.load(directory, config).get(0).getName());
		assertEquals(5, catalog.reads);
	}

	@Test void lr2AliasesStaySeparateAndSkinPathChangesAreReflected() throws Exception {
		Files.writeString(directory.resolve("seven.lr2skin"), "#INFORMATION,0,Seven,Author\n#CUSTOMFILE,Image,LR2files\\Theme\\*.png,a\n");
		Files.writeString(directory.resolve("fourteen.lr2skin"), "#INFORMATION,2,14key,Author\n");
		CountingCatalog catalog = new CountingCatalog();
		Config config = new Config();
		config.setSkinpath("first");
		List<SkinHeader> headers = catalog.load(directory, config);
		assertEquals(List.of(SkinType.PLAY_14KEYS, SkinType.PLAY_10KEYS,
				SkinType.PLAY_7KEYS, SkinType.PLAY_5KEYS), headers.stream().map(SkinHeader::getSkinType).toList());
		assertEquals("Seven (7KEYS) ", headers.get(3).getName());
		assertEquals("14key", headers.get(1).getName());
		assertEquals("first/*.png", headers.get(2).getCustomFiles()[0].path);
		config.setSkinpath("second");
		headers = catalog.load(directory, config);
		assertEquals(4, catalog.reads);
		assertEquals("second/*.png", headers.get(3).getCustomFiles()[0].path);
		assertNotSame(headers.get(2).getCustomOptions()[0], headers.get(3).getCustomOptions()[0]);
	}

	@Test void missingRootAndInvalidHeadersDoNotHideLaterValidSkins() throws Exception {
		Path missing = directory.resolve("new");
		CountingCatalog catalog = new CountingCatalog();
		Config config = new Config();
		assertTrue(catalog.load(missing, config).isEmpty());
		Files.createDirectory(missing);
		Files.writeString(missing.resolve("data.json"), "{}");
		Files.writeString(missing.resolve("valid.json"), "{\"type\":0,\"name\":\"Valid\"}");
		assertEquals("Valid", catalog.load(missing, config).get(0).getName());
		Files.writeString(missing.resolve("data.json"), "{\"type\":1,\"name\":\"Fixed\"}");
		assertEquals(2, catalog.load(missing, config).size());
	}

	@Test void changingRootNeverReusesAnotherInstallationsDefinitions() throws Exception {
		Path first = Files.createDirectory(directory.resolve("first"));
		Path second = Files.createDirectory(directory.resolve("second"));
		Files.writeString(first.resolve("skin.json"), "{\"type\":0,\"name\":\"First\"}");
		Files.writeString(second.resolve("skin.json"), "{\"type\":0,\"name\":\"Second\"}");
		SkinHeaderCatalog catalog = new SkinHeaderCatalog();
		assertEquals("First", catalog.load(first, new Config()).get(0).getName());
		assertEquals("Second", catalog.load(second, new Config()).get(0).getName());
	}

	@Test void explicitRefreshRereadsExternalDependenciesWithoutChangingTheSkinFile() throws Exception {
		Path external = Files.createTempFile("skin-header-external-", ".lua");
		try {
			Files.writeString(external, "return {type=0,name='Before'}");
			Files.writeString(directory.resolve("skin.luaskin"), "return dofile([=[" + external + "]=])");
			CountingCatalog catalog = new CountingCatalog();
			Config config = new Config();
			assertEquals("Before", catalog.load(directory, config).get(0).getName());
			Files.writeString(external, "return {type=0,name='After'}");
			catalog.invalidate();
			assertEquals("After", catalog.load(directory, config).get(0).getName());
			assertEquals(2, catalog.reads);
		} finally {
			Files.deleteIfExists(external);
		}
	}
}
