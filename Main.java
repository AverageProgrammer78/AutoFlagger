package wwt.varun;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.github.skjolber.packing.api.Box;
import com.github.skjolber.packing.api.BoxItem;
import com.github.skjolber.packing.api.Container;
import com.github.skjolber.packing.api.ContainerItem;
import com.github.skjolber.packing.api.Packager;
import com.github.skjolber.packing.api.PackagerResult;
import com.github.skjolber.packing.packer.laff.LargestAreaFitFirstPackager;
import com.github.skjolber.packing.visualizer.packaging.DefaultPackagingResultVisualizerFactory;

public class Main {
    public static void main(String[] args) throws Exception {
        Container container = Container.newBuilder()
            .withDescription("WWT-Main-Bin")
            .withSize(55, 10, 55)
            .withEmptyWeight(1)
            .withMaxLoadWeight(1000)
            .build();

        List<ContainerItem> containerItems = ContainerItem.newListBuilder()
            .withContainer(container)
            .build();

        List<BoxItem> products = new ArrayList<>();
        products.add(new BoxItem(Box.newBuilder().withId("Box1").withSize(10, 51, 10).withWeight(10).withRotate3D().build(), 1));

        Packager packager = LargestAreaFitFirstPackager.newBuilder().build();

        PackagerResult result = packager.newResultBuilder()
            .withContainerItems(containerItems)
            .withBoxItems(products)
            .build();

        if (result.isSuccess()) {
            System.out.println("LAFF Packing Success!");
            
            DefaultPackagingResultVisualizerFactory factory = new DefaultPackagingResultVisualizerFactory(false);
            
            List<Container> packedContainers = result.getContainers();

            File outputFile = new File("./visual.json");
            factory.visualize(packedContainers, outputFile);
            
            System.out.println("JSON exported to: " + outputFile.getAbsolutePath());
        } else {
            System.out.println("Packing failed with LAFF - items do not fit.");
        }
    }
}