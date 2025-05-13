# Imagen base para ejecución
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080
EXPOSE 8081

# Imagen para compilar
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
ARG BUILD_CONFIGURATION=Release
WORKDIR /src

# Copiar ambos .csproj antes de hacer restore
COPY ["Gastapp-API/Gastapp-API.csproj", "Gastapp-API/"]
COPY ["Gastapp.Models/Gastapp.Models.csproj", "Gastapp.Models/"]
RUN dotnet restore "Gastapp-API/Gastapp-API.csproj"

# Copiar el resto del código
COPY . .
WORKDIR "/src/Gastapp-API"
RUN dotnet build "Gastapp-API.csproj" -c $BUILD_CONFIGURATION -o /app/build

# Publicar
FROM build AS publish
ARG BUILD_CONFIGURATION=Release
RUN dotnet publish "Gastapp-API.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false

# Imagen final
FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Gastapp-API.dll"]
