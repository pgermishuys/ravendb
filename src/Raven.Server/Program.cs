using System;
using System.IO;
using System.Runtime.Loader;
using System.Threading;
using Raven.Server.Config;
using Raven.Server.Documents.Handlers.Debugging;
using Raven.Server.ServerWide.LowMemoryNotification;
using Raven.Server.Utils;
using Sparrow.Json.Parsing;
using Sparrow.Logging;

namespace Raven.Server
{
    public class Program
    {
        private static Logger _logger;

        public static int Main(string[] args)
        {
            WelcomeMessage.Print();

            var configuration = new RavenConfiguration();
            if (args != null)
            {
                configuration.AddCommandLine(args);
            }

            configuration.Initialize();

            LogMode mode;
            if (Enum.TryParse(configuration.Core.LogLevel, out mode) == false)
                mode = LogMode.Operations;

            LoggingSource.Instance.SetupLogMode(mode, Path.Combine(AppContext.BaseDirectory, configuration.Core.LogsDirectory));
            _logger = LoggingSource.Instance.GetLogger<Program>("Raven/Server");

            try
            {
                using (var server = new RavenServer(configuration))
                {
                    try
                    {
                        server.Initialize();
                        Console.WriteLine($"Listening to: {string.Join(", ", configuration.Core.ServerUrl)}");
                        Console.WriteLine("Server started, listening to requests...");

                        if (configuration.Core.RunAsService)
                        {
                            RunAsService();
                        }
                        else
                        {
                            RunInteractive(server);
                        }
                        Console.WriteLine("Starting shut down...");
                        if (_logger.IsInfoEnabled)
                            _logger.Info("Server is shutting down");
                    }
                    catch (Exception e)
                    {
                        if (_logger.IsOperationsEnabled)
                            _logger.Operations("Failed to initialize the server", e);
                        Console.WriteLine(e);
                        return -1;
                    }
                }
                Console.WriteLine("Shutdown completed");
                return 0;
            }
            catch (Exception e)
            {
                Console.WriteLine("Error during shutdown");
                Console.WriteLine(e);
                return -2;
            }
        }

        private static void RunAsService()
        {
            ManualResetEvent mre = new ManualResetEvent(false);
            if (_logger.IsInfoEnabled)
                _logger.Info("Server is running as a service");
            Console.WriteLine("Running as Service");
            AssemblyLoadContext.Default.Unloading += (s) =>
            {
                Console.WriteLine("Received graceful exit request...");
                mre.Set();
            };
            mre.WaitOne();
        }

        private static void RunInteractive(RavenServer server)
        {
            var configuration = server.Configuration;

            while (true)
            {

                switch (Console.ReadLine()?.ToLower())
                {                    
                    case "q":
                        return;

                    case "cls":
                        Console.Clear();
                        break;

                    case "log":
                        LoggingSource.Instance.EnableConsoleLogging();
                        LoggingSource.Instance.SetupLogMode(LogMode.Information,
                            Path.Combine(AppContext.BaseDirectory, configuration.Core.LogsDirectory));
                        break;

                    case "nolog":
                        LoggingSource.Instance.DisableConsoleLogging();
                        LoggingSource.Instance.SetupLogMode(LogMode.None,
                            Path.Combine(AppContext.BaseDirectory, configuration.Core.LogsDirectory));
                        break;

                    case "stats":
                        //stop dumping logs
                        LoggingSource.Instance.DisableConsoleLogging();
                        LoggingSource.Instance.SetupLogMode(LogMode.None,
                            Path.Combine(AppContext.BaseDirectory, configuration.Core.LogsDirectory));

                        Console.WriteLine("Showing stats, press ESC to close...");
                        Console.WriteLine(
                            "  Working Set   UnmanagedAllocations  ManagedAllocations  MemoryMapped  Requests/Sec");
                        var i = 0;
                        while (Console.KeyAvailable == false || Console.ReadKey(true).Key != ConsoleKey.Escape)
                        {
                            var json = MemoryStatsHandler.MemoryStatsInternal();
                            var humaneProp = (json["Humane"] as DynamicJsonValue);
                            var workingSet = humaneProp?["WorkingSet"];
                            var totalUnmanaged = humaneProp?["TotalUnmanagedAllocations"];
                            var managedMemory = humaneProp?["ManagedAllocations"];
                            var totalMapping = humaneProp?["TotalMemoryMapped"];
                            var reqCounter = server.Metrics.RequestsPerSecondCounter.Count;

                            Console.Write(
                                $"{(i++%2 == 0 ? '*' : '+')} {workingSet,-15} {totalUnmanaged,-20} {managedMemory,-19} {totalMapping,-15} {reqCounter,-15}\r");

                            Thread.Sleep(1000);
                        }
                        Console.WriteLine();
                        Console.WriteLine("Stats halted");

                        break;

                    case "oom":
                    case "low-mem":
                    case "low-memory":
                        AbstractLowMemoryNotification.Instance.SimulateLowMemoryNotification();
                        break;

                    case "help":
                        Console.WriteLine("Avaliable Commands :");
                        Console.WriteLine("[cls] : clear screen");
                        Console.WriteLine("[log]: dump logs to console");
                        Console.WriteLine("[nolog]: stop dumping logs to console");
                        Console.WriteLine("[low-mem] : simulate low memory");
                        Console.WriteLine("[stats]: dump statistical information");
                        Console.WriteLine("[q]: quit");
                        Console.WriteLine();
                        break;

                    default:
                        Console.WriteLine("Unknown command, type 'help' to get all of the avaliable commands");
                        break;
                }
            }
        }

    }
}
