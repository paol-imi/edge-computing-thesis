package main.commands;

import com.google.gson.Gson;
import main.infrastucture.Area;
import main.infrastucture.Infrastructure;
import main.utils.InfrastructureParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class DisplayInfrastructure {

    public static void displayInfrastructure(String fileName) {
        Gson g = new Gson();
        Infrastructure infrastructure = null;
        try {
            infrastructure = g.fromJson(Files.readString(Path.of(fileName)), Infrastructure.class);

            // Check correctness of infrastructure file.
            System.err.println("🔄 Checking if infrastructure is correct.");
            if (!InfrastructureParser.isInfrastructureJsonCorrect(infrastructure)) {
                System.err.println("❌ The infrastructure JSON is NOT correct.");
                return;
            }
            System.err.println("✅ The infrastructure JSON is correct.");

            print(infrastructure);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void print(Infrastructure inf) {
        System.out.println("Area Types Identifiers [" + inf.areaTypesIdentifiers.length + "]: " + Arrays.toString(inf.areaTypesIdentifiers));

        depthFirstPrint(inf.hierarchy[0], 0);
    }

    private static void depthFirstPrint(Area area, int level) {
        for (int i = 0;i < level;i++)
            System.out.print("    ");
        System.out.println(area.areaName);

        if (area.areas.length > 0)
            for(Area a : area.areas)
                depthFirstPrint(a, level + 1);
    }
}
