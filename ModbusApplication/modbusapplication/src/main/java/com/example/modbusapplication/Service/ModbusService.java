package com.example.modbusapplication.Service;

import com.example.modbusapplication.Model.ModbusRecord;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import java.time.LocalDateTime;
import com.google.protobuf.ByteString;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ModbusService {

    private final String plcIp = "192.168.1.1";
    private final int plcPort = 502;
    private final String filePath = "modbus-buffer.txt";
    private String deviceId = "RICE-MILL-01"; 
    int m =10;
    // @Scheduled(fixedRate = 10000)
    public void readModbusRegisters() {
        TCPMasterConnection connection = null;

        try {
            InetAddress address = InetAddress.getByName(plcIp);
            connection = new TCPMasterConnection(address);
            connection.setPort(plcPort);
            connection.connect();

            ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(0, 10);
            request.setUnitID(1);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();

            List<Integer> registerValues = new ArrayList<>();
            for (int i = 0; i < response.getWordCount(); i++) {
                registerValues.add(response.getRegister(i).getValue());
            }

            // ✅ Extract machine name from registers 2 to 10 (9 registers = 18 bytes)
            byte[] byteArray = new byte[16]; // 9 registers, each 2 bytes
            int index = 0;

            // Loop through registers 2 to 10 and get their low and high bytes
            for (int i = 2; i <=9 ; i++) {
                byteArray[index++] = (byte) (registerValues.get(i) & 0xFF);         // Low byte
                byteArray[index++] = (byte) ((registerValues.get(i) >> 8) & 0xFF);  // High byte
            }

           
            String batchName = new String(byteArray, StandardCharsets.UTF_8).trim();
            System.out.println("Machine Name from HMI: " + batchName);


            ZoneId zone = ZoneId.systemDefault();
            LocalDateTime now = LocalDateTime.now(zone);
            String localTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            List<ModbusRecord> recodsList = new ArrayList<>();
            recodsList.add(new ModbusRecord("datetime", localTime));
            recodsList.add(new ModbusRecord("batchName", batchName));
            recodsList.add(new ModbusRecord("setWeight", "" + registerValues.get(0)));
            recodsList.add(new ModbusRecord("actualWeight", "" + registerValues.get(1)));
            recodsList.add(new ModbusRecord("deviceId", deviceId));

            ByteString byteString = toByteString(recodsList);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                writer.write(Base64.getEncoder().encodeToString(byteString.toByteArray()));
                writer.newLine();
                System.out.println("✅ Wrote records as ByteString to file.");
            } catch (IOException e) {
                System.err.println("❌ Error writing to file: " + e.getMessage());
                e.printStackTrace();
            }

            // break;  // ✅ Exit the retry loop after successful read

        } catch (Exception e) {
            System.err.println("❌ Modbus error: " + e.getMessage());

            try {
                if (connection != null) connection.close();
                System.out.println("🔁 Retrying in 2 seconds...");
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // break;  // Exit if interrupted externally
            } catch (Exception ignored) {}
        }
    }
    
    public int readActualWeightOnly() {
        TCPMasterConnection connection = null;
        try {
            InetAddress address = InetAddress.getByName(plcIp);
            connection = new TCPMasterConnection(address);
            connection.setPort(plcPort);
            connection.connect();
    
            ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(1, 1); // reg 1 = actual weight
            request.setUnitID(1);
    
            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();
    
            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
            return response.getRegister(0).getValue();
    
        } catch (Exception e) {
            System.err.println("⚠️ Error reading actual weight: " + e.getMessage());
            return -1;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }
        }
        
    }
    public static ByteString toByteString(List<ModbusRecord> list) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(list);
        }
        return ByteString.copyFrom(bos.toByteArray());
    }
    public void increament(){
        System.out.println(m);
        m+=1;
    }

    public int peekActualWeight() {
        try {
            int[] registers = readRegistersRaw();
            if (registers.length > 1) {
                return registers[1]; // actual weight assumed at index 1
            }
            return -1;
        } catch (Exception e) {
            System.err.println("Error peeking weight: " + e.getMessage());
            return -1;
        }
    }
    public int[] readRegistersRaw() throws IOException {
        TCPMasterConnection connection = null;
        try {
            InetAddress address = InetAddress.getByName(plcIp);
            connection = new TCPMasterConnection(address);
            connection.setPort(plcPort);
            connection.connect();
    
            ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(0, 10);
            request.setUnitID(1);
    
            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();
    
            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
            int[] values = new int[response.getWordCount()];
            for (int i = 0; i < response.getWordCount(); i++) {
                values[i] = response.getRegister(i).getValue();
            }
            return values;
        } catch (Exception e) {
            throw new IOException("Failed to read Modbus registers", e);
        } finally {
            if (connection != null) connection.close();
        }
    }
    
   }
