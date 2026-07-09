package com.hrms.cms.config;

import com.hrms.cms.entity.Pincode;
import com.hrms.cms.repository.PincodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Order(5)
@Slf4j
public class PincodeDataInitializer implements CommandLineRunner {

    private final PincodeRepository pincodeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (pincodeRepository.count() > 0) {
            log.info("Pincode data already loaded ({} records)", pincodeRepository.count());
            return;
        }

        log.info("Loading pincode data from CSV...");
        try {
            ClassPathResource resource = new ClassPathResource("data/pincodes.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            String header = reader.readLine(); // skip header
            List<Pincode> batch = new ArrayList<>(1000);
            int count = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 6) continue;

                batch.add(Pincode.builder()
                    .pincode(parts[0].trim())
                    .officeName(parts[1].trim())
                    .district(parts[2].trim())
                    .state(parts[3].trim())
                    .region(parts[4].trim())
                    .division(parts[5].trim())
                    .officeType(parts.length > 6 ? parts[6].trim() : "B.O")
                    .build());

                if (batch.size() >= 1000) {
                    pincodeRepository.saveAll(batch);
                    count += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                pincodeRepository.saveAll(batch);
                count += batch.size();
            }

            reader.close();
            log.info("Loaded {} pincode records successfully", count);

        } catch (Exception e) {
            log.error("Failed to load pincode data: {}", e.getMessage());
            log.info("Falling back to inline pincode seed data...");
            seedInlinePincodes();
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private void seedInlinePincodes() {
        // Fallback: seed representative pincodes for all states/UTs
        List<Pincode> pincodes = new ArrayList<>();
        addStatePincodes(pincodes);
        pincodeRepository.saveAll(pincodes);
        log.info("Seeded {} inline pincode records as fallback", pincodes.size());
    }

    private void addStatePincodes(List<Pincode> list) {
        // Every state has at least a few representative pincodes for testing
        addPincodesForState(list, "Maharashtra", new String[][]{
            {"400001", "Mumbai GPO", "Mumbai", "Mumbai", "Mumbai"},
            {"400002", "Kalbadevi", "Mumbai", "Mumbai", "Mumbai"},
            {"400003", "Masjid Bunder", "Mumbai", "Mumbai", "Mumbai"},
            {"400004", "Girgaon", "Mumbai", "Mumbai", "Mumbai"},
            {"400005", "Colaba", "Mumbai", "Mumbai", "Mumbai"},
            {"400006", "Malabar Hill", "Mumbai", "Mumbai", "Mumbai"},
            {"400007", "Grant Road", "Mumbai", "Mumbai", "Mumbai"},
            {"400008", "Mumbai Central", "Mumbai", "Mumbai", "Mumbai"},
            {"400009", "Mahalaxmi", "Mumbai", "Mumbai", "Mumbai"},
            {"400010", "Mazgaon", "Mumbai", "Mumbai", "Mumbai"},
            {"400011", "Jacob Circle", "Mumbai", "Mumbai", "Mumbai"},
            {"400012", "Parel", "Mumbai", "Mumbai", "Mumbai"},
            {"400013", "Delisle Road", "Mumbai", "Mumbai", "Mumbai"},
            {"400014", "Dadar", "Mumbai", "Mumbai", "Mumbai"},
            {"400015", "Sewri", "Mumbai", "Mumbai", "Mumbai"},
            {"400016", "Mahim", "Mumbai", "Mumbai", "Mumbai"},
            {"400017", "Dharavi", "Mumbai", "Mumbai", "Mumbai"},
            {"400018", "Worli", "Mumbai", "Mumbai", "Mumbai"},
            {"400019", "Sion", "Mumbai", "Mumbai", "Mumbai"},
            {"400020", "Churchgate", "Mumbai", "Mumbai", "Mumbai"},
            {"400021", "Nariman Point", "Mumbai", "Mumbai", "Mumbai"},
            {"400022", "Chembur", "Mumbai", "Mumbai", "Mumbai"},
            {"400025", "Prabhadevi", "Mumbai", "Mumbai", "Mumbai"},
            {"400028", "Kurla", "Mumbai", "Mumbai", "Mumbai"},
            {"400029", "Bandra West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400030", "Bandra East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400037", "Antop Hill", "Mumbai", "Mumbai", "Mumbai"},
            {"400049", "Juhu", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400050", "Bandra Terminus", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400051", "Khar", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400052", "Santacruz West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400053", "Vile Parle East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400054", "Santacruz East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400055", "Vile Parle West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400056", "Andheri West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400057", "Andheri East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400058", "Jogeshwari West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400059", "Goregaon West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400060", "Jogeshwari East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400061", "Malad West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400062", "Goregaon East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400063", "Malad East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400064", "Kandivali West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400065", "Kandivali East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400066", "Borivali West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400067", "Borivali East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400068", "Dahisar", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400069", "Vikhroli", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400070", "Kurla West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400071", "Ghatkopar", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400072", "Bhandup", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400074", "Mulund", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400076", "Powai", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400078", "Bhandup West", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400080", "Airoli", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400086", "Ghatkopar East", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400088", "Tilak Nagar", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400089", "Sakinaka", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400091", "Nahur", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400093", "Powai IIT", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400097", "Malad Marve", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400099", "Gorai", "Mumbai Suburban", "Mumbai", "Mumbai"},
            {"400101", "Mira Road", "Thane", "Mumbai", "Mumbai"},
            {"410001", "Pune Cantonment", "Pune", "Pune", "Pune"},
            {"410002", "Pune Camp", "Pune", "Pune", "Pune"},
            {"411001", "Pune GPO", "Pune", "Pune", "Pune"},
            {"411002", "Shivaji Nagar", "Pune", "Pune", "Pune"},
            {"411003", "Sadashiv Peth", "Pune", "Pune", "Pune"},
            {"411004", "Deccan Gymkhana", "Pune", "Pune", "Pune"},
            {"411005", "Shaniwar Peth", "Pune", "Pune", "Pune"},
            {"411006", "Ganesh Khind", "Pune", "Pune", "Pune"},
            {"411007", "Aundh", "Pune", "Pune", "Pune"},
            {"411008", "Kothrud", "Pune", "Pune", "Pune"},
            {"411009", "Yerawada", "Pune", "Pune", "Pune"},
            {"411011", "Bibvewadi", "Pune", "Pune", "Pune"},
            {"411012", "Dhankawadi", "Pune", "Pune", "Pune"},
            {"411013", "Kondhwa", "Pune", "Pune", "Pune"},
            {"411014", "Hadapsar", "Pune", "Pune", "Pune"},
            {"411015", "Wanowrie", "Pune", "Pune", "Pune"},
            {"411016", "Sahakarnagar", "Pune", "Pune", "Pune"},
            {"411017", "Karve Nagar", "Pune", "Pune", "Pune"},
            {"411018", "Erandwane", "Pune", "Pune", "Pune"},
            {"411019", "Baner", "Pune", "Pune", "Pune"},
            {"411020", "Senapati Bapat Road", "Pune", "Pune", "Pune"},
            {"411021", "Paud Road", "Pune", "Pune", "Pune"},
            {"411027", "Pashan", "Pune", "Pune", "Pune"},
            {"411028", "Bavdhan", "Pune", "Pune", "Pune"},
            {"411030", "Warje", "Pune", "Pune", "Pune"},
            {"411032", "Pimpri", "Pune", "Pune", "Pune"},
            {"411033", "Chinchwad", "Pune", "Pune", "Pune"},
            {"411035", "Akurdi", "Pune", "Pune", "Pune"},
            {"411036", "Nigdi", "Pune", "Pune", "Pune"},
            {"411037", "Bhosari", "Pune", "Pune", "Pune"},
            {"411038", "Hinjewadi", "Pune", "Pune", "Pune"},
            {"411039", "Wakad", "Pune", "Pune", "Pune"},
            {"411041", "Wagholi", "Pune", "Pune", "Pune"},
            {"411043", "Kharadi", "Pune", "Pune", "Pune"},
            {"411044", "Viman Nagar", "Pune", "Pune", "Pune"},
            {"411045", "Magarpatta", "Pune", "Pune", "Pune"},
            {"411048", "Undri", "Pune", "Pune", "Pune"},
            {"411057", "Wakad", "Pune", "Pune", "Pune"},
            {"422001", "Nashik GPO", "Nashik", "Nashik", "Nashik"},
            {"422002", "Nashik Road", "Nashik", "Nashik", "Nashik"},
            {"422003", "Panchavati", "Nashik", "Nashik", "Nashik"},
            {"422005", "Deolali", "Nashik", "Nashik", "Nashik"},
            {"422007", "Satpur", "Nashik", "Nashik", "Nashik"},
            {"422009", "Cidco Nashik", "Nashik", "Nashik", "Nashik"},
            {"422010", "Ambad", "Nashik", "Nashik", "Nashik"},
            {"431001", "Aurangabad GPO", "Aurangabad", "Aurangabad", "Aurangabad"},
            {"431002", "Aurangabad City", "Aurangabad", "Aurangabad", "Aurangabad"},
            {"431003", "Cidco Aurangabad", "Aurangabad", "Aurangabad", "Aurangabad"},
            {"431005", "Aurangabad Cantonment", "Aurangabad", "Aurangabad", "Aurangabad"},
            {"440001", "Nagpur GPO", "Nagpur", "Nagpur", "Nagpur"},
            {"440002", "Sitabuldi", "Nagpur", "Nagpur", "Nagpur"},
            {"440003", "Sadar", "Nagpur", "Nagpur", "Nagpur"},
            {"440004", "Mahal", "Nagpur", "Nagpur", "Nagpur"},
            {"440005", "Itwari", "Nagpur", "Nagpur", "Nagpur"},
            {"440006", "Dharampeth", "Nagpur", "Nagpur", "Nagpur"},
            {"440008", "Laxmi Nagar", "Nagpur", "Nagpur", "Nagpur"},
            {"440009", "Civil Lines", "Nagpur", "Nagpur", "Nagpur"},
            {"440010", "Nagpur University", "Nagpur", "Nagpur", "Nagpur"},
            {"440012", "Manewada", "Nagpur", "Nagpur", "Nagpur"},
            {"440013", "Wardha Road", "Nagpur", "Nagpur", "Nagpur"},
            {"440014", "Nandanvan", "Nagpur", "Nagpur", "Nagpur"},
            {"440015", "Hingna", "Nagpur", "Nagpur", "Nagpur"},
            {"440017", "Koradi", "Nagpur", "Nagpur", "Nagpur"},
            {"440018", "Kamptee", "Nagpur", "Nagpur", "Nagpur"},
            {"440022", "Wadi", "Nagpur", "Nagpur", "Nagpur"},
            {"440024", "Khamla", "Nagpur", "Nagpur", "Nagpur"},
            {"440025", "Pratap Nagar", "Nagpur", "Nagpur", "Nagpur"},
            {"440027", "Trimurti Nagar", "Nagpur", "Nagpur", "Nagpur"},
            {"440030", "Besa", "Nagpur", "Nagpur", "Nagpur"},
            {"440034", "Hudkeshwar", "Nagpur", "Nagpur", "Nagpur"}
        });

        addPincodesForState(list, "Delhi", new String[][]{
            {"110001", "Parliament House", "New Delhi", "Delhi", "New Delhi"},
            {"110002", "Darya Ganj", "Central Delhi", "Delhi", "New Delhi"},
            {"110003", "CGO Complex", "New Delhi", "Delhi", "New Delhi"},
            {"110004", "Rashtrapati Bhavan", "New Delhi", "Delhi", "New Delhi"},
            {"110005", "Karol Bagh", "Central Delhi", "Delhi", "New Delhi"},
            {"110006", "Chandni Chowk", "Central Delhi", "Delhi", "New Delhi"},
            {"110007", "New Delhi Railway Station", "New Delhi", "Delhi", "New Delhi"},
            {"110008", "Patel Nagar", "West Delhi", "Delhi", "New Delhi"},
            {"110009", "Civil Lines", "North Delhi", "Delhi", "New Delhi"},
            {"110010", "Indraprastha", "New Delhi", "Delhi", "New Delhi"},
            {"110011", "Connaught Place", "New Delhi", "Delhi", "New Delhi"},
            {"110012", "Willingdon Crescent", "New Delhi", "Delhi", "New Delhi"},
            {"110013", "Lady Hardinge", "Central Delhi", "Delhi", "New Delhi"},
            {"110014", "Jangpura", "South Delhi", "Delhi", "New Delhi"},
            {"110015", "Krishna Nagar", "East Delhi", "Delhi", "New Delhi"},
            {"110016", "Hauz Khas", "South Delhi", "Delhi", "New Delhi"},
            {"110017", "Lodhi Road", "South Delhi", "Delhi", "New Delhi"},
            {"110018", "Tilak Nagar", "West Delhi", "Delhi", "New Delhi"},
            {"110019", "Kalkaji", "South Delhi", "Delhi", "New Delhi"},
            {"110020", "Okhla", "South Delhi", "Delhi", "New Delhi"},
            {"110021", "Mehrauli", "South Delhi", "Delhi", "New Delhi"},
            {"110022", "RK Puram", "South West Delhi", "Delhi", "New Delhi"},
            {"110023", "Lajpat Nagar", "South Delhi", "Delhi", "New Delhi"},
            {"110024", "Defence Colony", "South Delhi", "Delhi", "New Delhi"},
            {"110025", "Chanakyapuri", "New Delhi", "Delhi", "New Delhi"},
            {"110026", "Anand Parbat", "Central Delhi", "Delhi", "New Delhi"},
            {"110027", "Safdarjung", "South Delhi", "Delhi", "New Delhi"},
            {"110028", "Shankar Road", "Central Delhi", "Delhi", "New Delhi"},
            {"110029", "South Extension", "South Delhi", "Delhi", "New Delhi"},
            {"110030", "Saket", "South Delhi", "Delhi", "New Delhi"},
            {"110031", "Gandhi Nagar", "East Delhi", "Delhi", "New Delhi"},
            {"110032", "Shahdara", "Shahdara", "Delhi", "New Delhi"},
            {"110033", "Seelampur", "North East Delhi", "Delhi", "New Delhi"},
            {"110034", "Punjabi Bagh", "West Delhi", "Delhi", "New Delhi"},
            {"110035", "Shakur Basti", "North West Delhi", "Delhi", "New Delhi"},
            {"110036", "Alipur", "North Delhi", "Delhi", "New Delhi"},
            {"110037", "Sarojini Nagar", "South West Delhi", "Delhi", "New Delhi"},
            {"110038", "Inder Puri", "South West Delhi", "Delhi", "New Delhi"},
            {"110039", "Moti Bagh", "South West Delhi", "Delhi", "New Delhi"},
            {"110040", "Vasant Vihar", "South West Delhi", "Delhi", "New Delhi"},
            {"110041", "Shakti Nagar", "North Delhi", "Delhi", "New Delhi"},
            {"110042", "Model Town", "North West Delhi", "Delhi", "New Delhi"},
            {"110043", "Kirti Nagar", "West Delhi", "Delhi", "New Delhi"},
            {"110044", "Laxmi Nagar", "East Delhi", "Delhi", "New Delhi"},
            {"110045", "Raja Garden", "West Delhi", "Delhi", "New Delhi"},
            {"110046", "Rajouri Garden", "West Delhi", "Delhi", "New Delhi"},
            {"110047", "South Moti Bagh", "South West Delhi", "Delhi", "New Delhi"},
            {"110048", "Greater Kailash", "South Delhi", "Delhi", "New Delhi"},
            {"110049", "East of Kailash", "South Delhi", "Delhi", "New Delhi"},
            {"110051", "Preet Vihar", "East Delhi", "Delhi", "New Delhi"},
            {"110052", "Pitam Pura", "North West Delhi", "Delhi", "New Delhi"},
            {"110053", "Laxmi Nagar", "East Delhi", "Delhi", "New Delhi"},
            {"110054", "Mall Road", "North Delhi", "Delhi", "New Delhi"},
            {"110055", "Paharganj", "Central Delhi", "Delhi", "New Delhi"},
            {"110056", "Azadpur", "North West Delhi", "Delhi", "New Delhi"},
            {"110057", "Vasant Kunj", "South West Delhi", "Delhi", "New Delhi"},
            {"110058", "Janakpuri", "West Delhi", "Delhi", "New Delhi"},
            {"110059", "Uttam Nagar", "West Delhi", "Delhi", "New Delhi"},
            {"110060", "Hari Nagar", "West Delhi", "Delhi", "New Delhi"},
            {"110061", "Palam", "South West Delhi", "Delhi", "New Delhi"},
            {"110062", "Dwarka", "South West Delhi", "Delhi", "New Delhi"},
            {"110063", "Sagarpur", "South West Delhi", "Delhi", "New Delhi"},
            {"110064", "Najafgarh", "South West Delhi", "Delhi", "New Delhi"},
            {"110065", "Okhla Industrial", "South Delhi", "Delhi", "New Delhi"},
            {"110066", "Tughlakabad", "South Delhi", "Delhi", "New Delhi"},
            {"110067", "Munirka", "South West Delhi", "Delhi", "New Delhi"},
            {"110068", "Naraina", "West Delhi", "Delhi", "New Delhi"},
            {"110070", "IGI Airport", "South West Delhi", "Delhi", "New Delhi"},
            {"110071", "Chattarpur", "South Delhi", "Delhi", "New Delhi"},
            {"110072", "Mundka", "West Delhi", "Delhi", "New Delhi"},
            {"110073", "Nangloi", "West Delhi", "Delhi", "New Delhi"},
            {"110074", "Mayur Vihar Phase III", "East Delhi", "Delhi", "New Delhi"},
            {"110075", "Dwarka Sector 8", "South West Delhi", "Delhi", "New Delhi"},
            {"110076", "Jasola", "South Delhi", "Delhi", "New Delhi"},
            {"110077", "Sarita Vihar", "South Delhi", "Delhi", "New Delhi"},
            {"110078", "Dwarka Sector 23", "South West Delhi", "Delhi", "New Delhi"},
            {"110080", "Rohini Sector 3", "North West Delhi", "Delhi", "New Delhi"},
            {"110081", "Rithala", "North West Delhi", "Delhi", "New Delhi"},
            {"110082", "Dilshad Garden", "Shahdara", "Delhi", "New Delhi"},
            {"110083", "Shalimar Bagh", "North West Delhi", "Delhi", "New Delhi"},
            {"110084", "Burari", "North Delhi", "Delhi", "New Delhi"},
            {"110085", "Rohini Sector 11", "North West Delhi", "Delhi", "New Delhi"},
            {"110086", "Bawana", "North Delhi", "Delhi", "New Delhi"},
            {"110087", "Sector 17 Rohini", "North West Delhi", "Delhi", "New Delhi"},
            {"110088", "Begumpur", "North West Delhi", "Delhi", "New Delhi"},
            {"110089", "Rohini Sector 24", "North West Delhi", "Delhi", "New Delhi"},
            {"110091", "Mayur Vihar Phase I", "East Delhi", "Delhi", "New Delhi"},
            {"110092", "Patparganj", "East Delhi", "Delhi", "New Delhi"},
            {"110093", "Nand Nagri", "North East Delhi", "Delhi", "New Delhi"},
            {"110094", "Gharoli", "East Delhi", "Delhi", "New Delhi"},
            {"110095", "Noida Link Road", "East Delhi", "Delhi", "New Delhi"},
            {"110096", "Vasundhara Enclave", "East Delhi", "Delhi", "New Delhi"}
        });

        addPincodesForState(list, "Karnataka", new String[][]{
            {"560001", "Bangalore GPO", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560002", "Frazer Town", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560003", "Shivaji Nagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560004", "Basavanagudi", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560005", "Seshadripuram", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560008", "Chamrajpet", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560009", "Richmond Town", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560010", "Jayanagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560011", "Malleshwaram", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560012", "Indiranagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560016", "Rajajinagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560017", "Marathahalli", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560018", "Sadashivanagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560019", "BTM Layout", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560020", "Yeshwantpur", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560024", "JP Nagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560025", "Bannerghatta Road", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560027", "Ulsoor", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560029", "Whitefield", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560030", "Adugodi", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560032", "Vijayanagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560033", "Hebbal", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560034", "HSR Layout", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560035", "RT Nagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560036", "Yelahanka", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560037", "Koramangala", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560038", "Electronic City", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560040", "Wilson Garden", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560041", "Banashankari", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560043", "Majestic", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560045", "Nagarbhavi", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560047", "HAL Airport", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560048", "Sarjapur Road", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560050", "Bellandur", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560052", "CV Raman Nagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560054", "Hennur", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560055", "Thanisandra", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560058", "Tumkur Road", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560060", "Kengeri", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560062", "Jakkur", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560064", "Devanahalli", "Bangalore Rural", "Bangalore", "Bangalore"},
            {"560066", "Anekal", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560068", "Uttarahalli", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560070", "Peenya", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560073", "Rajarajeshwari Nagar", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560076", "Kadugodi", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560078", "Hoodi", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560085", "Bommanahalli", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560094", "Sarjapur", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560097", "Chandapura", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"560100", "KR Puram", "Bangalore Urban", "Bangalore", "Bangalore"},
            {"570001", "Mysore GPO", "Mysore", "Mysore", "Mysore"},
            {"570002", "Mysore Fort", "Mysore", "Mysore", "Mysore"},
            {"570004", "Saraswathipuram", "Mysore", "Mysore", "Mysore"},
            {"570005", "Kuvempunagar", "Mysore", "Mysore", "Mysore"},
            {"570006", "Vijayanagar", "Mysore", "Mysore", "Mysore"},
            {"570008", "Jayalakshmipuram", "Mysore", "Mysore", "Mysore"},
            {"570010", "Hebbal", "Mysore", "Mysore", "Mysore"},
            {"570017", "Bogadi", "Mysore", "Mysore", "Mysore"},
            {"580001", "Hubli GPO", "Dharwad", "Dharwad", "Dharwad"},
            {"580020", "Dharwad", "Dharwad", "Dharwad", "Dharwad"},
            {"580021", "Hubli New", "Dharwad", "Dharwad", "Dharwad"},
            {"590001", "Belgaum GPO", "Belgaum", "Belgaum", "Belgaum"},
            {"590003", "Belgaum Camp", "Belgaum", "Belgaum", "Belgaum"},
            {"575001", "Mangalore GPO", "Dakshina Kannada", "Mangalore", "Mangalore"},
            {"575002", "Mangalore City", "Dakshina Kannada", "Mangalore", "Mangalore"},
            {"575003", "Bunder", "Dakshina Kannada", "Mangalore", "Mangalore"}
        });

        addPincodesForState(list, "Tamil Nadu", new String[][]{
            {"600001", "Chennai GPO", "Chennai", "Chennai", "Chennai"},
            {"600002", "Triplicane", "Chennai", "Chennai", "Chennai"},
            {"600003", "Park Town", "Chennai", "Chennai", "Chennai"},
            {"600004", "Georgetown", "Chennai", "Chennai", "Chennai"},
            {"600005", "Vepery", "Chennai", "Chennai", "Chennai"},
            {"600006", "Nungambakkam", "Chennai", "Chennai", "Chennai"},
            {"600007", "Chetpet", "Chennai", "Chennai", "Chennai"},
            {"600008", "Egmore", "Chennai", "Chennai", "Chennai"},
            {"600009", "Saidapet", "Chennai", "Chennai", "Chennai"},
            {"600010", "Mylapore", "Chennai", "Chennai", "Chennai"},
            {"600011", "Perambur", "Chennai", "Chennai", "Chennai"},
            {"600012", "Perambur Barracks", "Chennai", "Chennai", "Chennai"},
            {"600014", "T Nagar", "Chennai", "Chennai", "Chennai"},
            {"600015", "Adyar", "Chennai", "Chennai", "Chennai"},
            {"600017", "Kodambakkam", "Chennai", "Chennai", "Chennai"},
            {"600018", "Royapettah", "Chennai", "Chennai", "Chennai"},
            {"600020", "Anna Nagar", "Chennai", "Chennai", "Chennai"},
            {"600024", "Guindy", "Chennai", "Chennai", "Chennai"},
            {"600025", "Mandaveli", "Chennai", "Chennai", "Chennai"},
            {"600028", "Besant Nagar", "Chennai", "Chennai", "Chennai"},
            {"600030", "Adambakkam", "Chennai", "Chennai", "Chennai"},
            {"600032", "Ashok Nagar", "Chennai", "Chennai", "Chennai"},
            {"600034", "Kilpauk", "Chennai", "Chennai", "Chennai"},
            {"600035", "Teynampet", "Chennai", "Chennai", "Chennai"},
            {"600036", "Velachery", "Chennai", "Chennai", "Chennai"},
            {"600040", "Anna Nagar West", "Chennai", "Chennai", "Chennai"},
            {"600041", "Ambattur", "Chennai", "Chennai", "Chennai"},
            {"600042", "Villivakkam", "Chennai", "Chennai", "Chennai"},
            {"600044", "Chromepet", "Chennai", "Chennai", "Chennai"},
            {"600045", "Pallavaram", "Chennai", "Chennai", "Chennai"},
            {"600047", "Tambaram", "Chennai", "Chennai", "Chennai"},
            {"600048", "Velachery", "Chennai", "Chennai", "Chennai"},
            {"600050", "Alwarpet", "Chennai", "Chennai", "Chennai"},
            {"600053", "OMR", "Chennai", "Chennai", "Chennai"},
            {"600056", "Porur", "Chennai", "Chennai", "Chennai"},
            {"600058", "Mogappair", "Chennai", "Chennai", "Chennai"},
            {"600059", "Korattur", "Chennai", "Chennai", "Chennai"},
            {"600061", "Poonamallee", "Chennai", "Chennai", "Chennai"},
            {"600062", "Avadi", "Chennai", "Chennai", "Chennai"},
            {"600063", "Madhavaram", "Chennai", "Chennai", "Chennai"},
            {"600073", "Sholinganallur", "Chennai", "Chennai", "Chennai"},
            {"600077", "Perungudi", "Chennai", "Chennai", "Chennai"},
            {"600083", "Thiruvanmiyur", "Chennai", "Chennai", "Chennai"},
            {"600085", "Thoraipakkam", "Chennai", "Chennai", "Chennai"},
            {"600089", "Medavakkam", "Chennai", "Chennai", "Chennai"},
            {"600091", "Siruseri", "Kanchipuram", "Chennai", "Chennai"},
            {"600096", "Kelambakkam", "Kanchipuram", "Chennai", "Chennai"},
            {"600097", "OMR Navalur", "Kanchipuram", "Chennai", "Chennai"},
            {"600119", "Mahabalipuram", "Kanchipuram", "Chennai", "Chennai"},
            {"625001", "Madurai GPO", "Madurai", "Madurai", "Madurai"},
            {"625002", "Madurai Fort", "Madurai", "Madurai", "Madurai"},
            {"625003", "Simmakkal", "Madurai", "Madurai", "Madurai"},
            {"625009", "Anna Nagar", "Madurai", "Madurai", "Madurai"},
            {"625010", "KK Nagar", "Madurai", "Madurai", "Madurai"},
            {"641001", "Coimbatore GPO", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641002", "RS Puram", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641003", "Town Hall", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641004", "Peelamedu", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641006", "Gandhipuram", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641011", "Race Course", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641012", "Saibaba Colony", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641014", "Singanallur", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641018", "Ganapathy", "Coimbatore", "Coimbatore", "Coimbatore"},
            {"641035", "Saravanampatti", "Coimbatore", "Coimbatore", "Coimbatore"}
        });

        addPincodesForState(list, "Uttar Pradesh", new String[][]{
            {"201001", "Ghaziabad GPO", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201002", "Ghaziabad City", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201003", "Kavi Nagar", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201005", "Mohan Nagar", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201009", "Vaishali", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201010", "Indirapuram", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201012", "Raj Nagar Extension", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201014", "Crossing Republik", "Ghaziabad", "Ghaziabad", "Ghaziabad"},
            {"201301", "Noida Sector 1", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201303", "Noida Sector 15", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201304", "Noida Sector 62", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201305", "Noida Sector 18", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201306", "Greater Noida", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201307", "Greater Noida West", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"201310", "Noida Sector 44", "Gautam Buddha Nagar", "Noida", "Noida"},
            {"208001", "Kanpur GPO", "Kanpur Nagar", "Kanpur", "Kanpur"},
            {"208002", "Kanpur Cantonment", "Kanpur Nagar", "Kanpur", "Kanpur"},
            {"208003", "Govind Nagar", "Kanpur Nagar", "Kanpur", "Kanpur"},
            {"208004", "Kidwai Nagar", "Kanpur Nagar", "Kanpur", "Kanpur"},
            {"208005", "Swaroop Nagar", "Kanpur Nagar", "Kanpur", "Kanpur"},
            {"211001", "Allahabad GPO", "Allahabad", "Allahabad", "Allahabad"},
            {"211002", "Civil Lines", "Allahabad", "Allahabad", "Allahabad"},
            {"211003", "Tagore Town", "Allahabad", "Allahabad", "Allahabad"},
            {"221001", "Varanasi GPO", "Varanasi", "Varanasi", "Varanasi"},
            {"221002", "Varanasi Cantonment", "Varanasi", "Varanasi", "Varanasi"},
            {"221003", "Bhelupur", "Varanasi", "Varanasi", "Varanasi"},
            {"221005", "Lanka", "Varanasi", "Varanasi", "Varanasi"},
            {"221010", "BHU", "Varanasi", "Varanasi", "Varanasi"},
            {"226001", "Lucknow GPO", "Lucknow", "Lucknow", "Lucknow"},
            {"226002", "Aminabad", "Lucknow", "Lucknow", "Lucknow"},
            {"226003", "Charbagh", "Lucknow", "Lucknow", "Lucknow"},
            {"226004", "Aliganj", "Lucknow", "Lucknow", "Lucknow"},
            {"226005", "Mahanagar", "Lucknow", "Lucknow", "Lucknow"},
            {"226006", "Indira Nagar", "Lucknow", "Lucknow", "Lucknow"},
            {"226010", "Gomti Nagar", "Lucknow", "Lucknow", "Lucknow"},
            {"226012", "Vikas Nagar", "Lucknow", "Lucknow", "Lucknow"},
            {"226016", "Jankipuram", "Lucknow", "Lucknow", "Lucknow"},
            {"226020", "Chinhat", "Lucknow", "Lucknow", "Lucknow"},
            {"226021", "Gomti Nagar Extension", "Lucknow", "Lucknow", "Lucknow"},
            {"226022", "Shaheed Path", "Lucknow", "Lucknow", "Lucknow"},
            {"226024", "Sushant Golf City", "Lucknow", "Lucknow", "Lucknow"},
            {"226025", "Raebareli Road", "Lucknow", "Lucknow", "Lucknow"},
            {"226028", "Amar Shaheed Path", "Lucknow", "Lucknow", "Lucknow"},
            {"250001", "Meerut GPO", "Meerut", "Meerut", "Meerut"},
            {"250002", "Cantonment", "Meerut", "Meerut", "Meerut"},
            {"250004", "Shastri Nagar", "Meerut", "Meerut", "Meerut"},
            {"282001", "Agra GPO", "Agra", "Agra", "Agra"},
            {"282002", "Agra Cantonment", "Agra", "Agra", "Agra"},
            {"282003", "Tajganj", "Agra", "Agra", "Agra"},
            {"282004", "Kamla Nagar", "Agra", "Agra", "Agra"},
            {"282005", "Dayal Bagh", "Agra", "Agra", "Agra"},
            {"282007", "Sikandra", "Agra", "Agra", "Agra"}
        });

        addPincodesForState(list, "West Bengal", new String[][]{
            {"700001", "Kolkata GPO", "Kolkata", "Kolkata", "Kolkata"},
            {"700002", "Bow Bazar", "Kolkata", "Kolkata", "Kolkata"},
            {"700003", "Moulali", "Kolkata", "Kolkata", "Kolkata"},
            {"700004", "Cossipore", "Kolkata", "Kolkata", "Kolkata"},
            {"700005", "Bagbazar", "Kolkata", "Kolkata", "Kolkata"},
            {"700006", "College Street", "Kolkata", "Kolkata", "Kolkata"},
            {"700007", "Entally", "Kolkata", "Kolkata", "Kolkata"},
            {"700008", "Kidderpore", "Kolkata", "Kolkata", "Kolkata"},
            {"700009", "Hastings", "Kolkata", "Kolkata", "Kolkata"},
            {"700010", "Maidan", "Kolkata", "Kolkata", "Kolkata"},
            {"700012", "Dharmatala", "Kolkata", "Kolkata", "Kolkata"},
            {"700013", "Alipore", "Kolkata", "Kolkata", "Kolkata"},
            {"700014", "Behala", "Kolkata", "Kolkata", "Kolkata"},
            {"700016", "Elgin Road", "Kolkata", "Kolkata", "Kolkata"},
            {"700017", "Ballygunge", "Kolkata", "Kolkata", "Kolkata"},
            {"700019", "Park Street", "Kolkata", "Kolkata", "Kolkata"},
            {"700020", "Kalighat", "Kolkata", "Kolkata", "Kolkata"},
            {"700025", "Gariahat", "Kolkata", "Kolkata", "Kolkata"},
            {"700026", "Dhakuria", "Kolkata", "Kolkata", "Kolkata"},
            {"700027", "Shakespeare Sarani", "Kolkata", "Kolkata", "Kolkata"},
            {"700029", "Tollygunge", "Kolkata", "Kolkata", "Kolkata"},
            {"700030", "New Alipore", "Kolkata", "Kolkata", "Kolkata"},
            {"700031", "Jadavpur", "Kolkata", "Kolkata", "Kolkata"},
            {"700032", "Garia", "Kolkata", "Kolkata", "Kolkata"},
            {"700033", "Lake Gardens", "Kolkata", "Kolkata", "Kolkata"},
            {"700034", "Sealdah", "Kolkata", "Kolkata", "Kolkata"},
            {"700035", "Shyambazar", "Kolkata", "Kolkata", "Kolkata"},
            {"700036", "Beliaghata", "Kolkata", "Kolkata", "Kolkata"},
            {"700037", "Ultadanga", "Kolkata", "Kolkata", "Kolkata"},
            {"700039", "Dumdum", "Kolkata", "Kolkata", "Kolkata"},
            {"700040", "Bidhan Nagar", "Kolkata", "Kolkata", "Kolkata"},
            {"700042", "Howrah", "Howrah", "Howrah", "Howrah"},
            {"700046", "Salt Lake", "Kolkata", "Kolkata", "Kolkata"},
            {"700054", "Lake Town", "Kolkata", "Kolkata", "Kolkata"},
            {"700055", "Bangur Avenue", "Kolkata", "Kolkata", "Kolkata"},
            {"700058", "Dunlop", "Kolkata", "Kolkata", "Kolkata"},
            {"700059", "Baranagar", "Kolkata", "Kolkata", "Kolkata"},
            {"700064", "Salt Lake Sector V", "Kolkata", "Kolkata", "Kolkata"},
            {"700091", "New Town", "Kolkata", "Kolkata", "Kolkata"},
            {"700098", "Rajarhat", "Kolkata", "Kolkata", "Kolkata"},
            {"700099", "Newtown Action Area", "Kolkata", "Kolkata", "Kolkata"},
            {"700101", "Madhyamgram", "North 24 Parganas", "Kolkata", "Kolkata"},
            {"700102", "Barasat", "North 24 Parganas", "Kolkata", "Kolkata"},
            {"700104", "Naihati", "North 24 Parganas", "Kolkata", "Kolkata"},
            {"700106", "Habra", "North 24 Parganas", "Kolkata", "Kolkata"},
            {"711101", "Howrah GPO", "Howrah", "Howrah", "Howrah"},
            {"711102", "Shibpur", "Howrah", "Howrah", "Howrah"},
            {"711104", "Belur", "Howrah", "Howrah", "Howrah"}
        });

        addPincodesForState(list, "Gujarat", new String[][]{
            {"380001", "Ahmedabad GPO", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380002", "Kalupur", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380004", "Khanpur", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380005", "Paldi", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380006", "Navrangpura", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380007", "Ellis Bridge", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380008", "Ambawadi", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380009", "Ashram Road", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380013", "Satellite", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380014", "Thaltej", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380015", "Bodakdev", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380016", "Prahladnagar", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380019", "Memnagar", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380021", "Shahibag", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380022", "Vatva", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380024", "Maninagar", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380027", "Naranpura", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380050", "Gota", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380051", "Chandkheda", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380052", "Motera", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380054", "Science City", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380058", "SG Highway", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380059", "Bopal", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"380060", "South Bopal", "Ahmedabad", "Ahmedabad", "Ahmedabad"},
            {"382007", "Gandhinagar", "Gandhinagar", "Ahmedabad", "Gandhinagar"},
            {"382010", "Gandhinagar Sector 10", "Gandhinagar", "Ahmedabad", "Gandhinagar"},
            {"382016", "Gandhinagar Sector 21", "Gandhinagar", "Ahmedabad", "Gandhinagar"},
            {"382421", "GIFT City", "Gandhinagar", "Ahmedabad", "Gandhinagar"},
            {"390001", "Vadodara GPO", "Vadodara", "Vadodara", "Vadodara"},
            {"390002", "Alkapuri", "Vadodara", "Vadodara", "Vadodara"},
            {"390004", "Fatehganj", "Vadodara", "Vadodara", "Vadodara"},
            {"390005", "Sayaji Ganj", "Vadodara", "Vadodara", "Vadodara"},
            {"390007", "Manjalpur", "Vadodara", "Vadodara", "Vadodara"},
            {"390008", "Karelibaug", "Vadodara", "Vadodara", "Vadodara"},
            {"390019", "Waghodia Road", "Vadodara", "Vadodara", "Vadodara"},
            {"395001", "Surat GPO", "Surat", "Surat", "Surat"},
            {"395002", "Nanpura", "Surat", "Surat", "Surat"},
            {"395003", "Athwa Lines", "Surat", "Surat", "Surat"},
            {"395004", "Ring Road", "Surat", "Surat", "Surat"},
            {"395005", "Udhna", "Surat", "Surat", "Surat"},
            {"395006", "Adajan", "Surat", "Surat", "Surat"},
            {"395007", "Varachha", "Surat", "Surat", "Surat"},
            {"395009", "Katargam", "Surat", "Surat", "Surat"},
            {"395010", "Pal", "Surat", "Surat", "Surat"},
            {"395017", "Vesu", "Surat", "Surat", "Surat"},
            {"360001", "Rajkot GPO", "Rajkot", "Rajkot", "Rajkot"},
            {"360002", "Rajkot City", "Rajkot", "Rajkot", "Rajkot"},
            {"360005", "University Road", "Rajkot", "Rajkot", "Rajkot"}
        });

        addPincodesForState(list, "Rajasthan", new String[][]{
            {"302001", "Jaipur GPO", "Jaipur", "Jaipur", "Jaipur"},
            {"302002", "Johari Bazar", "Jaipur", "Jaipur", "Jaipur"},
            {"302003", "Bapu Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302004", "C-Scheme", "Jaipur", "Jaipur", "Jaipur"},
            {"302005", "Ashok Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302006", "Bani Park", "Jaipur", "Jaipur", "Jaipur"},
            {"302012", "Mansarovar", "Jaipur", "Jaipur", "Jaipur"},
            {"302015", "Vaishali Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302016", "Malviya Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302017", "Tonk Road", "Jaipur", "Jaipur", "Jaipur"},
            {"302018", "Sanganer", "Jaipur", "Jaipur", "Jaipur"},
            {"302019", "Pratap Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302020", "Jhotwara", "Jaipur", "Jaipur", "Jaipur"},
            {"302021", "Vidhyadhar Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302022", "Jagatpura", "Jaipur", "Jaipur", "Jaipur"},
            {"302026", "Sitapura", "Jaipur", "Jaipur", "Jaipur"},
            {"302027", "Ajmer Road", "Jaipur", "Jaipur", "Jaipur"},
            {"302029", "Mahesh Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302033", "Jawahar Nagar", "Jaipur", "Jaipur", "Jaipur"},
            {"302034", "Amer", "Jaipur", "Jaipur", "Jaipur"},
            {"305001", "Ajmer GPO", "Ajmer", "Ajmer", "Ajmer"},
            {"311001", "Bhilwara GPO", "Bhilwara", "Ajmer", "Bhilwara"},
            {"312001", "Chittorgarh", "Chittorgarh", "Udaipur", "Chittorgarh"},
            {"313001", "Udaipur GPO", "Udaipur", "Udaipur", "Udaipur"},
            {"313002", "Udaipur City", "Udaipur", "Udaipur", "Udaipur"},
            {"313004", "Hiran Magri", "Udaipur", "Udaipur", "Udaipur"},
            {"324001", "Kota GPO", "Kota", "Kota", "Kota"},
            {"324002", "Kota City", "Kota", "Kota", "Kota"},
            {"324005", "Talwandi", "Kota", "Kota", "Kota"},
            {"332001", "Sikar", "Sikar", "Jaipur", "Sikar"},
            {"334001", "Bikaner GPO", "Bikaner", "Bikaner", "Bikaner"},
            {"335001", "Sri Ganganagar", "Sri Ganganagar", "Bikaner", "Sri Ganganagar"},
            {"342001", "Jodhpur GPO", "Jodhpur", "Jodhpur", "Jodhpur"},
            {"342002", "Jodhpur City", "Jodhpur", "Jodhpur", "Jodhpur"},
            {"342003", "Paota", "Jodhpur", "Jodhpur", "Jodhpur"},
            {"342005", "Sardarpura", "Jodhpur", "Jodhpur", "Jodhpur"},
            {"342008", "Ratanada", "Jodhpur", "Jodhpur", "Jodhpur"}
        });

        addPincodesForState(list, "Telangana", new String[][]{
            {"500001", "Hyderabad GPO", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500002", "Nampally", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500003", "Koti", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500004", "Sultan Bazar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500005", "Afzalgunj", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500006", "Padmarao Nagar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500007", "Begumpet", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500008", "Ameerpet", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500009", "Banjara Hills", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500010", "Somajiguda", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500012", "Mehdipatnam", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500013", "Tolichowki", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500016", "Ameerpet", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500018", "Himayat Nagar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500019", "Dilsukhnagar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500020", "Santosh Nagar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500024", "Lakdikapul", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500025", "Malakpet", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500027", "Uppal", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500028", "LB Nagar", "Rangareddy", "Hyderabad", "Hyderabad"},
            {"500032", "Jubilee Hills", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500033", "Masab Tank", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500034", "Punjagutta", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500038", "HITEC City", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500039", "Kondapur", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500040", "Kukatpally", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500044", "Miyapur", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500045", "Bachupally", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500046", "Chandanagar", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500049", "Manikonda", "Rangareddy", "Hyderabad", "Hyderabad"},
            {"500050", "Gachibowli", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500051", "Nanakramguda", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500072", "Secunderabad", "Hyderabad", "Hyderabad", "Hyderabad"},
            {"500081", "Kompally", "Medchal-Malkajgiri", "Hyderabad", "Hyderabad"},
            {"500084", "Shamshabad", "Rangareddy", "Hyderabad", "Hyderabad"},
            {"500089", "Financial District", "Rangareddy", "Hyderabad", "Hyderabad"},
            {"500090", "Madhapur", "Hyderabad", "Hyderabad", "Hyderabad"}
        });

        addPincodesForState(list, "Andhra Pradesh", new String[][]{
            {"520001", "Vijayawada GPO", "Krishna", "Vijayawada", "Vijayawada"},
            {"520002", "Vijayawada City", "Krishna", "Vijayawada", "Vijayawada"},
            {"520003", "One Town", "Krishna", "Vijayawada", "Vijayawada"},
            {"520007", "Benz Circle", "Krishna", "Vijayawada", "Vijayawada"},
            {"520008", "Auto Nagar", "Krishna", "Vijayawada", "Vijayawada"},
            {"520010", "Moghalrajpuram", "Krishna", "Vijayawada", "Vijayawada"},
            {"530001", "Visakhapatnam GPO", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530002", "Waltair", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530003", "Daba Gardens", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530004", "Akkayyapalem", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530016", "MVP Colony", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530017", "Seethammadhara", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530018", "Gajuwaka", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530024", "Madhurawada", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530032", "Rushikonda", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530041", "Pendurthi", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"530045", "Gajuwaka Industrial", "Visakhapatnam", "Visakhapatnam", "Visakhapatnam"},
            {"522001", "Guntur GPO", "Guntur", "Guntur", "Guntur"},
            {"522002", "Arundelpet", "Guntur", "Guntur", "Guntur"},
            {"515001", "Anantapur GPO", "Anantapur", "Anantapur", "Anantapur"},
            {"516001", "Kadapa GPO", "YSR Kadapa", "Kadapa", "Kadapa"},
            {"517001", "Tirupati GPO", "Chittoor", "Tirupati", "Tirupati"},
            {"517501", "Tirupati Main", "Chittoor", "Tirupati", "Tirupati"},
            {"518001", "Kurnool GPO", "Kurnool", "Kurnool", "Kurnool"},
            {"524001", "Nellore GPO", "Nellore", "Nellore", "Nellore"}
        });

        addPincodesForState(list, "Kerala", new String[][]{
            {"695001", "Thiruvananthapuram GPO", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"695002", "Thampanoor", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"695003", "Vazhuthacaud", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"695004", "Kowdiar", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"695010", "Kazhakkoottam", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"695014", "Technopark", "Thiruvananthapuram", "Thiruvananthapuram", "Thiruvananthapuram"},
            {"682001", "Kochi GPO", "Ernakulam", "Kochi", "Kochi"},
            {"682002", "Fort Kochi", "Ernakulam", "Kochi", "Kochi"},
            {"682011", "Ernakulam North", "Ernakulam", "Kochi", "Kochi"},
            {"682015", "Ernakulam South", "Ernakulam", "Kochi", "Kochi"},
            {"682016", "Palarivattom", "Ernakulam", "Kochi", "Kochi"},
            {"682017", "Edappally", "Ernakulam", "Kochi", "Kochi"},
            {"682019", "Kakkanad", "Ernakulam", "Kochi", "Kochi"},
            {"682020", "Infopark", "Ernakulam", "Kochi", "Kochi"},
            {"682021", "Kaloor", "Ernakulam", "Kochi", "Kochi"},
            {"682024", "MG Road Kochi", "Ernakulam", "Kochi", "Kochi"},
            {"682025", "Marine Drive", "Ernakulam", "Kochi", "Kochi"},
            {"682030", "Aluva", "Ernakulam", "Kochi", "Kochi"},
            {"682301", "Tripunithura", "Ernakulam", "Kochi", "Kochi"},
            {"673001", "Kozhikode GPO", "Kozhikode", "Kozhikode", "Kozhikode"},
            {"673002", "Kozhikode Beach", "Kozhikode", "Kozhikode", "Kozhikode"},
            {"673004", "Palayam", "Kozhikode", "Kozhikode", "Kozhikode"},
            {"673016", "Mavoor Road", "Kozhikode", "Kozhikode", "Kozhikode"},
            {"680001", "Thrissur GPO", "Thrissur", "Thrissur", "Thrissur"},
            {"680002", "Thrissur Round", "Thrissur", "Thrissur", "Thrissur"},
            {"686001", "Kottayam GPO", "Kottayam", "Kottayam", "Kottayam"}
        });

        addPincodesForState(list, "Punjab", new String[][]{
            {"160001", "Chandigarh GPO", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160002", "Sector 7", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160003", "Sector 9", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160009", "Sector 17", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160011", "Sector 22", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160014", "Sector 14", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160017", "Sector 35", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160019", "Sector 43", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"160036", "PU Campus", "Chandigarh", "Chandigarh", "Chandigarh"},
            {"140001", "Ludhiana GPO", "Ludhiana", "Ludhiana", "Ludhiana"},
            {"140301", "Ludhiana City", "Ludhiana", "Ludhiana", "Ludhiana"},
            {"143001", "Amritsar GPO", "Amritsar", "Amritsar", "Amritsar"},
            {"143002", "Hall Bazar", "Amritsar", "Amritsar", "Amritsar"},
            {"143005", "Lawrence Road", "Amritsar", "Amritsar", "Amritsar"},
            {"144001", "Jalandhar GPO", "Jalandhar", "Jalandhar", "Jalandhar"},
            {"144002", "Jalandhar City", "Jalandhar", "Jalandhar", "Jalandhar"},
            {"147001", "Patiala GPO", "Patiala", "Patiala", "Patiala"},
            {"148001", "Sangrur", "Sangrur", "Patiala", "Sangrur"},
            {"151001", "Bathinda", "Bathinda", "Bathinda", "Bathinda"},
            {"152001", "Ferozepur", "Ferozepur", "Ferozepur", "Ferozepur"}
        });

        addPincodesForState(list, "Madhya Pradesh", new String[][]{
            {"462001", "Bhopal GPO", "Bhopal", "Bhopal", "Bhopal"},
            {"462002", "TT Nagar", "Bhopal", "Bhopal", "Bhopal"},
            {"462003", "New Market", "Bhopal", "Bhopal", "Bhopal"},
            {"462010", "MP Nagar", "Bhopal", "Bhopal", "Bhopal"},
            {"462011", "Kolar Road", "Bhopal", "Bhopal", "Bhopal"},
            {"462016", "Habibganj", "Bhopal", "Bhopal", "Bhopal"},
            {"462023", "Ayodhya Nagar", "Bhopal", "Bhopal", "Bhopal"},
            {"462026", "Hoshangabad Road", "Bhopal", "Bhopal", "Bhopal"},
            {"452001", "Indore GPO", "Indore", "Indore", "Indore"},
            {"452002", "Rajwada", "Indore", "Indore", "Indore"},
            {"452003", "Palasia", "Indore", "Indore", "Indore"},
            {"452004", "Sapna Sangeeta", "Indore", "Indore", "Indore"},
            {"452005", "Bhawarkuan", "Indore", "Indore", "Indore"},
            {"452006", "Scheme 54", "Indore", "Indore", "Indore"},
            {"452007", "AB Road", "Indore", "Indore", "Indore"},
            {"452009", "Vijay Nagar", "Indore", "Indore", "Indore"},
            {"452010", "Super Corridor", "Indore", "Indore", "Indore"},
            {"452012", "Nipania", "Indore", "Indore", "Indore"},
            {"482001", "Jabalpur GPO", "Jabalpur", "Jabalpur", "Jabalpur"},
            {"482002", "Sadar", "Jabalpur", "Jabalpur", "Jabalpur"},
            {"474001", "Gwalior GPO", "Gwalior", "Gwalior", "Gwalior"},
            {"474002", "Lashkar", "Gwalior", "Gwalior", "Gwalior"}
        });

        addPincodesForState(list, "Bihar", new String[][]{
            {"800001", "Patna GPO", "Patna", "Patna", "Patna"},
            {"800002", "Bankipur", "Patna", "Patna", "Patna"},
            {"800003", "Gulzarbagh", "Patna", "Patna", "Patna"},
            {"800004", "Patna City", "Patna", "Patna", "Patna"},
            {"800006", "Kankarbagh", "Patna", "Patna", "Patna"},
            {"800007", "Rajendra Nagar", "Patna", "Patna", "Patna"},
            {"800008", "Ashiana", "Patna", "Patna", "Patna"},
            {"800009", "Patliputra Colony", "Patna", "Patna", "Patna"},
            {"800010", "Naubatpur", "Patna", "Patna", "Patna"},
            {"800013", "Boring Road", "Patna", "Patna", "Patna"},
            {"800014", "Exhibition Road", "Patna", "Patna", "Patna"},
            {"800020", "Bailey Road", "Patna", "Patna", "Patna"},
            {"800025", "Danapur", "Patna", "Patna", "Patna"},
            {"812001", "Bhagalpur", "Bhagalpur", "Bhagalpur", "Bhagalpur"},
            {"842001", "Muzaffarpur", "Muzaffarpur", "Muzaffarpur", "Muzaffarpur"},
            {"846001", "Darbhanga", "Darbhanga", "Darbhanga", "Darbhanga"},
            {"854001", "Purnia", "Purnia", "Purnia", "Purnia"}
        });

        addPincodesForState(list, "Odisha", new String[][]{
            {"751001", "Bhubaneswar GPO", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751002", "Unit 2", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751003", "Unit 3", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751004", "Saheed Nagar", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751005", "Acharya Vihar", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751006", "Jaydev Vihar", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751007", "Nayapalli", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751009", "Chandrasekharpur", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751010", "Patia", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751012", "Infocity", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"751024", "Khandagiri", "Khordha", "Bhubaneswar", "Bhubaneswar"},
            {"753001", "Cuttack GPO", "Cuttack", "Cuttack", "Cuttack"},
            {"753002", "Cuttack City", "Cuttack", "Cuttack", "Cuttack"},
            {"769001", "Rourkela GPO", "Sundargarh", "Rourkela", "Rourkela"},
            {"760001", "Berhampur", "Ganjam", "Berhampur", "Berhampur"}
        });

        addPincodesForState(list, "Assam", new String[][]{
            {"781001", "Guwahati GPO", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781003", "Paltan Bazar", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781005", "Ulubari", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781006", "Beltola", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781007", "Dispur", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781009", "Khanapara", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781014", "Zoo Road", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781016", "Maligaon", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781019", "Garchuk", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"781034", "GS Road", "Kamrup Metropolitan", "Guwahati", "Guwahati"},
            {"786001", "Dibrugarh", "Dibrugarh", "Dibrugarh", "Dibrugarh"},
            {"785001", "Jorhat", "Jorhat", "Jorhat", "Jorhat"}
        });

        addPincodesForState(list, "Jharkhand", new String[][]{
            {"834001", "Ranchi GPO", "Ranchi", "Ranchi", "Ranchi"},
            {"834002", "Ranchi City", "Ranchi", "Ranchi", "Ranchi"},
            {"834003", "Morabadi", "Ranchi", "Ranchi", "Ranchi"},
            {"834004", "Ashok Nagar", "Ranchi", "Ranchi", "Ranchi"},
            {"834005", "Bariatu", "Ranchi", "Ranchi", "Ranchi"},
            {"834008", "Doranda", "Ranchi", "Ranchi", "Ranchi"},
            {"834009", "Kanke", "Ranchi", "Ranchi", "Ranchi"},
            {"826001", "Dhanbad GPO", "Dhanbad", "Dhanbad", "Dhanbad"},
            {"831001", "Jamshedpur GPO", "East Singhbhum", "Jamshedpur", "Jamshedpur"},
            {"831002", "Bistupur", "East Singhbhum", "Jamshedpur", "Jamshedpur"},
            {"831003", "Sakchi", "East Singhbhum", "Jamshedpur", "Jamshedpur"},
            {"814001", "Deoghar", "Deoghar", "Deoghar", "Deoghar"},
            {"827001", "Bokaro", "Bokaro", "Bokaro", "Bokaro"}
        });

        addPincodesForState(list, "Haryana", new String[][]{
            {"122001", "Gurugram GPO", "Gurugram", "Gurugram", "Gurugram"},
            {"122002", "DLF Phase 1", "Gurugram", "Gurugram", "Gurugram"},
            {"122003", "Sushant Lok", "Gurugram", "Gurugram", "Gurugram"},
            {"122004", "Udyog Vihar", "Gurugram", "Gurugram", "Gurugram"},
            {"122009", "Golf Course Road", "Gurugram", "Gurugram", "Gurugram"},
            {"122015", "Sector 56", "Gurugram", "Gurugram", "Gurugram"},
            {"122017", "Sohna Road", "Gurugram", "Gurugram", "Gurugram"},
            {"122018", "Cyber City", "Gurugram", "Gurugram", "Gurugram"},
            {"121001", "Faridabad GPO", "Faridabad", "Faridabad", "Faridabad"},
            {"121002", "NIT Faridabad", "Faridabad", "Faridabad", "Faridabad"},
            {"121003", "Sector 14 Faridabad", "Faridabad", "Faridabad", "Faridabad"},
            {"121004", "Ballabgarh", "Faridabad", "Faridabad", "Faridabad"},
            {"121006", "Sector 28 Faridabad", "Faridabad", "Faridabad", "Faridabad"},
            {"131001", "Sonipat", "Sonipat", "Sonipat", "Sonipat"},
            {"132001", "Karnal", "Karnal", "Karnal", "Karnal"},
            {"134001", "Ambala Cantonment", "Ambala", "Ambala", "Ambala"},
            {"125001", "Hisar", "Hisar", "Hisar", "Hisar"},
            {"126001", "Jind", "Jind", "Jind", "Jind"},
            {"127001", "Bhiwani", "Bhiwani", "Bhiwani", "Bhiwani"},
            {"124001", "Rohtak", "Rohtak", "Rohtak", "Rohtak"},
            {"136001", "Kaithal", "Kaithal", "Karnal", "Kaithal"}
        });

        addPincodesForState(list, "Chhattisgarh", new String[][]{
            {"492001", "Raipur GPO", "Raipur", "Raipur", "Raipur"},
            {"492007", "Shankar Nagar", "Raipur", "Raipur", "Raipur"},
            {"492009", "Civil Lines Raipur", "Raipur", "Raipur", "Raipur"},
            {"490001", "Durg GPO", "Durg", "Durg", "Durg"},
            {"490006", "Bhilai", "Durg", "Durg", "Durg"},
            {"490009", "Supela", "Durg", "Durg", "Durg"},
            {"495001", "Bilaspur GPO", "Bilaspur", "Bilaspur", "Bilaspur"},
            {"497001", "Korba", "Korba", "Bilaspur", "Korba"}
        });

        addPincodesForState(list, "Uttarakhand", new String[][]{
            {"248001", "Dehradun GPO", "Dehradun", "Dehradun", "Dehradun"},
            {"248002", "Rajpur Road", "Dehradun", "Dehradun", "Dehradun"},
            {"248003", "Dalanwala", "Dehradun", "Dehradun", "Dehradun"},
            {"248005", "Clock Tower", "Dehradun", "Dehradun", "Dehradun"},
            {"248006", "Vasant Vihar", "Dehradun", "Dehradun", "Dehradun"},
            {"248007", "Race Course", "Dehradun", "Dehradun", "Dehradun"},
            {"248009", "ISBT Dehradun", "Dehradun", "Dehradun", "Dehradun"},
            {"249401", "Haridwar", "Haridwar", "Haridwar", "Haridwar"},
            {"263001", "Nainital GPO", "Nainital", "Nainital", "Nainital"},
            {"263002", "Haldwani", "Nainital", "Nainital", "Nainital"},
            {"263139", "Rudrapur", "Udham Singh Nagar", "Nainital", "Rudrapur"}
        });

        addPincodesForState(list, "Himachal Pradesh", new String[][]{
            {"171001", "Shimla GPO", "Shimla", "Shimla", "Shimla"},
            {"171002", "Chhota Shimla", "Shimla", "Shimla", "Shimla"},
            {"171003", "Sanjauli", "Shimla", "Shimla", "Shimla"},
            {"171004", "Lakkar Bazar", "Shimla", "Shimla", "Shimla"},
            {"171005", "Summer Hill", "Shimla", "Shimla", "Shimla"},
            {"171006", "Mashobra", "Shimla", "Shimla", "Shimla"},
            {"176001", "Dharamsala", "Kangra", "Kangra", "Dharamsala"},
            {"175001", "Mandi", "Mandi", "Mandi", "Mandi"},
            {"173001", "Solan", "Solan", "Solan", "Solan"},
            {"177001", "Hamirpur", "Hamirpur", "Hamirpur", "Hamirpur"}
        });

        addPincodesForState(list, "Goa", new String[][]{
            {"403001", "Panaji", "North Goa", "Panaji", "Panaji"},
            {"403002", "Patto", "North Goa", "Panaji", "Panaji"},
            {"403004", "Dona Paula", "North Goa", "Panaji", "Panaji"},
            {"403005", "Porvorim", "North Goa", "Panaji", "Panaji"},
            {"403501", "Mapusa", "North Goa", "Panaji", "Panaji"},
            {"403516", "Calangute", "North Goa", "Panaji", "Panaji"},
            {"403601", "Margao", "South Goa", "Panaji", "Margao"},
            {"403602", "Fatorda", "South Goa", "Panaji", "Margao"},
            {"403726", "Vasco da Gama", "South Goa", "Panaji", "Margao"}
        });

        addPincodesForState(list, "Jammu and Kashmir", new String[][]{
            {"190001", "Srinagar GPO", "Srinagar", "Srinagar", "Srinagar"},
            {"190002", "Lal Chowk", "Srinagar", "Srinagar", "Srinagar"},
            {"190003", "Rajbagh", "Srinagar", "Srinagar", "Srinagar"},
            {"190006", "Hyderpora", "Srinagar", "Srinagar", "Srinagar"},
            {"190008", "Natipora", "Srinagar", "Srinagar", "Srinagar"},
            {"190010", "Hazratbal", "Srinagar", "Srinagar", "Srinagar"},
            {"180001", "Jammu GPO", "Jammu", "Jammu", "Jammu"},
            {"180002", "Gandhi Nagar", "Jammu", "Jammu", "Jammu"},
            {"180004", "Canal Road", "Jammu", "Jammu", "Jammu"},
            {"180005", "Rehari", "Jammu", "Jammu", "Jammu"}
        });
    }

    private void addPincodesForState(List<Pincode> list, String state, String[][] data) {
        for (String[] row : data) {
            list.add(Pincode.builder()
                .pincode(row[0])
                .officeName(row[1])
                .district(row[2])
                .state(state)
                .region(row[3])
                .division(row[4])
                .officeType("H.O")
                .build());
        }
    }
}
